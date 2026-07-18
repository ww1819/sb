import http from '@/api/http'

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

function isZipMagic(buffer: ArrayBuffer) {
  const sig = new Uint8Array(buffer)
  return sig.length >= 4 && sig[0] === 0x50 && sig[1] === 0x4b && sig[2] === 0x03 && sig[3] === 0x04
}

async function readBlobError(blob: Blob): Promise<string> {
  const text = await blob.text()
  try {
    const json = JSON.parse(text) as { message?: string; code?: number }
    if (json.message) return json.message
  } catch {
    /* not json */
  }
  return text.slice(0, 200) || '下载失败'
}

export function resolveTemplateFilename(templateUrl: string, fallback = 'import_template.xlsx') {
  const parts = templateUrl.split('/').filter(Boolean)
  const importIdx = parts.lastIndexOf('import')
  const name = importIdx > 0 ? parts[importIdx - 1] : parts[parts.length - 1] || 'template'
  return `${name}_import_template.xlsx`
}

/** 存库/href 常带 `/api` 前缀；axios baseURL 已是 `/api`，需去掉避免双前缀 */
export function toHttpPath(url: string) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('/api/')) return url.slice(4)
  if (url.startsWith('/api')) return url.slice(4) || '/'
  return url.startsWith('/') ? url : `/${url}`
}

/** 带登录态拉取附件并弹窗预览（兼容旧调用名） */
export async function previewApiFile(url: string, title = '附件预览') {
  const { openFilePreview } = await import('@/composables/useFilePreview')
  await openFilePreview(url, title)
}

export async function downloadApiFile(url: string, filename = 'download.xlsx') {
  const res = await http.get(toHttpPath(url), { responseType: 'blob' })
  const blob = res.data as Blob

  const contentType = (res.headers['content-type'] as string | undefined)?.toLowerCase() ?? ''
  if (contentType.includes('application/json') || contentType.includes('text/plain')) {
    throw new Error(await readBlobError(blob))
  }

  const head = await blob.slice(0, 4).arrayBuffer()
  const expectXlsx = filename.toLowerCase().endsWith('.xlsx') || contentType.includes('spreadsheetml')
  if (expectXlsx && !isZipMagic(head)) {
    throw new Error(await readBlobError(blob))
  }

  const outType = expectXlsx ? XLSX_MIME : (contentType || blob.type || 'application/octet-stream')
  const outBlob = blob.type === outType ? blob : new Blob([blob], { type: outType })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(outBlob)
  const disposition = res.headers['content-disposition'] as string | undefined
  const matched = disposition?.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i)
  const fromHeader = matched ? decodeURIComponent(matched[1] || matched[2]) : ''
  link.download = fromHeader || filename
  link.click()
  URL.revokeObjectURL(link.href)
}
