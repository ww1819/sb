import { reactive } from 'vue'
import http from '@/api/http'
import { toHttpPath } from '@/utils/fileDownload'

export type FilePreviewKind = 'image' | 'pdf' | 'other'

const state = reactive({
  visible: false,
  loading: false,
  title: '附件预览',
  objectUrl: '',
  mime: '',
  kind: 'other' as FilePreviewKind,
  error: '',
  sourceUrl: ''
})

function guessMime(url: string, fallback = 'application/octet-stream') {
  const lower = url.toLowerCase().split('?')[0]
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg'
  if (lower.endsWith('.gif')) return 'image/gif'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.bmp')) return 'image/bmp'
  if (lower.endsWith('.pdf')) return 'application/pdf'
  return fallback
}

function resolveKind(mime: string): FilePreviewKind {
  if (mime.startsWith('image/')) return 'image'
  if (mime === 'application/pdf' || mime.includes('pdf')) return 'pdf'
  return 'other'
}

async function readBlobError(blob: Blob): Promise<string> {
  const text = await blob.text()
  try {
    const json = JSON.parse(text) as { message?: string }
    if (json.message) return json.message
  } catch {
    /* not json */
  }
  return text.slice(0, 200) || '预览失败'
}

function revoke() {
  if (state.objectUrl) {
    URL.revokeObjectURL(state.objectUrl)
    state.objectUrl = ''
  }
}

export function closeFilePreview() {
  state.visible = false
  state.loading = false
  state.error = ''
  revoke()
}

/** 带登录态拉取附件，在页面弹窗中预览（不新开浏览器标签） */
export async function openFilePreview(url: string, title = '附件预览') {
  if (!url) return
  revoke()
  state.visible = true
  state.loading = true
  state.title = title
  state.error = ''
  state.kind = 'other'
  state.mime = ''
  state.sourceUrl = url
  try {
    const path = toHttpPath(url)
    const res = await http.get(path, { responseType: 'blob' })
    let blob = res.data as Blob
    const contentType = (res.headers['content-type'] as string | undefined)?.toLowerCase() ?? ''
    // 鉴权失败等也可能返回 JSON，但 Content-Type 偶发仍是 octet-stream
    if (
      contentType.includes('application/json') ||
      contentType.includes('text/plain') ||
      (blob.type && blob.type.includes('json'))
    ) {
      throw new Error(await readBlobError(blob))
    }
    const headerMime = contentType.split(';')[0]?.trim() || ''
    const mime =
      headerMime && headerMime !== 'application/octet-stream'
        ? headerMime
        : guessMime(url, blob.type || 'application/octet-stream')
    // 空文件或极小响应可能是错误体
    if (blob.size < 32 && mime === 'application/octet-stream') {
      const maybeErr = await readBlobError(blob)
      if (maybeErr && !maybeErr.startsWith('PK')) {
        throw new Error(maybeErr || '预览失败')
      }
    }
    if (!blob.type || blob.type === 'application/octet-stream') {
      blob = new Blob([blob], { type: mime })
    }
    state.mime = mime
    state.kind = resolveKind(mime)
    state.objectUrl = URL.createObjectURL(blob)
  } catch (e: unknown) {
    state.error = e instanceof Error && e.message ? e.message : '预览失败'
  } finally {
    state.loading = false
  }
}

export function useFilePreviewState() {
  return state
}
