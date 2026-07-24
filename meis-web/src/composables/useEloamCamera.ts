import { ref, shallowRef, unref, type MaybeRef } from 'vue'
import { getCameraVendor, loadPreferredVendorId } from '@/config/cameraVendors'

/** 新良田 / Eloam 本机服务默认地址（PLT-CAM-01） */
export const ELOAM_WS_URL = 'ws://127.0.0.1:9000'

function resolveWsUrl(explicit?: MaybeRef<string | undefined>) {
  const v = explicit != null ? unref(explicit) : undefined
  if (v && v.trim()) return v.trim()
  const vendor = getCameraVendor(loadPreferredVendorId())
  return vendor.wsUrl || ELOAM_WS_URL
}

export type EloamMsg = {
  function?: string
  ret?: number
  value?: string | number
  type?: boolean
  [key: string]: unknown
}

type Pending = {
  resolve: (msg: EloamMsg) => void
  reject: (err: Error) => void
  timer: ReturnType<typeof setTimeout>
}

/**
 * 精简封装：连接 / 初始化 / 预览 / 拍照 / 纠偏 / 关闭。
 * 不包含身份证、人脸、PDF、打印等演示能力。
 * @param wsUrl 可传 ref；空则读当前优选厂家（PLT-CAM-02）
 */
export function useEloamCamera(wsUrl?: MaybeRef<string | undefined>) {
  const connected = ref(false)
  const cameraOpen = ref(false)
  const busy = ref(false)
  const error = ref('')
  const previewDataUrl = ref('')
  const deviceCount = ref(0)
  const deviceIndex = ref(0)
  const resolutionIndex = ref(0)
  const resolutions = ref<string[]>([])
  const deskewOn = ref(false)
  const lastWsUrl = ref(resolveWsUrl(wsUrl))

  const ws = shallowRef<WebSocket | null>(null)
  const pendingByFn = new Map<string, Pending[]>()

  function clearPending(fn: string, err?: Error) {
    const list = pendingByFn.get(fn)
    if (!list?.length) return
    pendingByFn.delete(fn)
    for (const p of list) {
      clearTimeout(p.timer)
      if (err) p.reject(err)
    }
  }

  function rejectAll(err: Error) {
    for (const fn of [...pendingByFn.keys()]) clearPending(fn, err)
  }

  function waitFor(fn: string, timeoutMs = 8000): Promise<EloamMsg> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const list = pendingByFn.get(fn) || []
        pendingByFn.set(
          fn,
          list.filter((p) => p.timer !== timer)
        )
        reject(new Error(`高拍仪响应超时：${fn}`))
      }, timeoutMs)
      const list = pendingByFn.get(fn) || []
      list.push({ resolve, reject, timer })
      pendingByFn.set(fn, list)
    })
  }

  function send(body: Record<string, unknown>) {
    const sock = ws.value
    if (!sock || sock.readyState !== WebSocket.OPEN) {
      throw new Error('未连接到高拍仪本机服务（请确认已安装并运行 Eloam，端口 9000）')
    }
    sock.send(JSON.stringify(body))
  }

  function onMessage(raw: EloamMsg) {
    const name = String(raw.function || '')
    if (name === 'ImageCallback') {
      if (cameraOpen.value && raw.value != null && String(raw.value)) {
        previewDataUrl.value = `data:image/jpeg;base64,${raw.value}`
      }
      return
    }

    if (name === 'GetDeviceCount' && raw.ret != null && raw.ret >= 0) {
      deviceCount.value = Number(raw.value) || 0
    }
    if (name === 'GetResolution' && raw.ret != null && raw.ret >= 0 && raw.value != null) {
      resolutions.value = String(raw.value)
        .split('|')
        .map((s) => s.trim())
        .filter(Boolean)
    }
    if (name === 'OpenCamera' && raw.ret != null && raw.ret >= 0) {
      cameraOpen.value = true
    }
    if (name === 'CloseCamera') {
      cameraOpen.value = false
      previewDataUrl.value = ''
    }
    if (name === 'SetDeskew' && raw.ret != null && raw.ret >= 0) {
      /* deskewOn 由调用方设置 */
    }

    const list = pendingByFn.get(name)
    if (list?.length) {
      const p = list.shift()!
      if (!list.length) pendingByFn.delete(name)
      else pendingByFn.set(name, list)
      clearTimeout(p.timer)
      if (raw.ret != null && raw.ret < 0) {
        p.reject(new Error(`高拍仪指令失败：${name}（ret=${raw.ret}）`))
      } else {
        p.resolve(raw)
      }
    }
  }

  function connect(): Promise<void> {
    const url = resolveWsUrl(wsUrl)
    if (!url) {
      return Promise.reject(new Error('当前厂家未配置本机服务地址（待接入）'))
    }
    if (ws.value && ws.value.readyState === WebSocket.OPEN && lastWsUrl.value === url) {
      connected.value = true
      return Promise.resolve()
    }
    if (ws.value) {
      try {
        ws.value.close()
      } catch {
        /* ignore */
      }
      ws.value = null
    }
    lastWsUrl.value = url
    error.value = ''
    return new Promise((resolve, reject) => {
      let settled = false
      let sock: WebSocket
      try {
        sock = new WebSocket(url)
      } catch (e) {
        const msg = e instanceof Error ? e.message : '无法创建 WebSocket'
        error.value = msg
        reject(new Error(msg))
        return
      }
      ws.value = sock
      const fail = (msg: string) => {
        if (settled) return
        settled = true
        connected.value = false
        error.value = msg
        reject(new Error(msg))
      }
      sock.onopen = () => {
        connected.value = true
        error.value = ''
        if (!settled) {
          settled = true
          resolve()
        }
      }
      sock.onmessage = (evt) => {
        if (typeof evt.data !== 'string' || !evt.data) return
        try {
          onMessage(JSON.parse(evt.data) as EloamMsg)
        } catch {
          /* ignore malformed */
        }
      }
      sock.onerror = () => {
        fail(`未检测到高拍仪本机服务（${url}）。请安装并启动对应厂家驱动后重试；亦可使用普通上传。`)
      }
      sock.onclose = () => {
        connected.value = false
        cameraOpen.value = false
        previewDataUrl.value = ''
        rejectAll(new Error('高拍仪连接已断开'))
        if (!settled) {
          fail('高拍仪连接已关闭')
        }
      }
    })
  }

  function disconnect() {
    try {
      if (cameraOpen.value) {
        try {
          send({ function: 'CloseCamera', device: deviceIndex.value })
        } catch {
          /* ignore */
        }
      }
      ws.value?.close()
    } catch {
      /* ignore */
    }
    ws.value = null
    connected.value = false
    cameraOpen.value = false
    previewDataUrl.value = ''
  }

  async function initAndOpen() {
    busy.value = true
    error.value = ''
    try {
      await connect()
      send({ function: 'InitDevs' })
      await waitFor('InitDevs')
      send({ function: 'GetDeviceCount' })
      await waitFor('GetDeviceCount')
      if (deviceCount.value <= 0) {
        throw new Error('未检测到高拍仪摄像头设备')
      }
      send({ function: 'GetResolution', device: deviceIndex.value })
      try {
        await waitFor('GetResolution', 5000)
      } catch {
        /* 分辨率可选 */
      }
      send({ function: 'SetDeskew', isdeskew: deskewOn.value ? 1 : 0 })
      try {
        await waitFor('SetDeskew', 3000)
      } catch {
        /* 纠偏可选 */
      }
      send({
        function: 'OpenCamera',
        device: deviceIndex.value,
        resolution: resolutionIndex.value,
        datacallback: true
      })
      await waitFor('OpenCamera')
    } catch (e) {
      const msg = e instanceof Error ? e.message : '打开高拍仪失败'
      error.value = msg
      throw e
    } finally {
      busy.value = false
    }
  }

  async function scanImage(): Promise<string> {
    if (!cameraOpen.value) throw new Error('请先打开摄像头预览')
    busy.value = true
    error.value = ''
    try {
      send({
        function: 'ScanImage',
        imagepath: '',
        colorize: 0,
        type: true
      })
      const msg = await waitFor('ScanImage', 15000)
      const b64 = msg.value != null ? String(msg.value) : ''
      if (!b64) throw new Error('拍照未返回图像数据')
      return b64
    } catch (e) {
      const msg = e instanceof Error ? e.message : '拍照失败'
      error.value = msg
      throw e
    } finally {
      busy.value = false
    }
  }

  async function setDeskew(on: boolean) {
    deskewOn.value = on
    if (!connected.value) return
    try {
      send({ function: 'SetDeskew', isdeskew: on ? 1 : 0 })
      await waitFor('SetDeskew', 3000)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '设置纠偏失败'
    }
  }

  async function closeCamera() {
    if (!connected.value) {
      cameraOpen.value = false
      previewDataUrl.value = ''
      return
    }
    try {
      send({ function: 'CloseCamera', device: deviceIndex.value })
      await waitFor('CloseCamera', 5000).catch(() => undefined)
    } finally {
      cameraOpen.value = false
      previewDataUrl.value = ''
    }
  }

  return {
    connected,
    cameraOpen,
    busy,
    error,
    previewDataUrl,
    deviceCount,
    deviceIndex,
    resolutionIndex,
    resolutions,
    deskewOn,
    lastWsUrl,
    connect,
    disconnect,
    initAndOpen,
    scanImage,
    setDeskew,
    closeCamera
  }
}

/** base64（可无 data: 前缀）→ File */
export function eloamBase64ToFile(base64: string, filename = `eloam-${Date.now()}.jpg`): File {
  const pure = base64.includes(',') ? base64.split(',')[1]! : base64
  const bin = atob(pure)
  const len = bin.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i)
  return new File([bytes], filename, { type: 'image/jpeg' })
}
