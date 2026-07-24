/** 高拍仪厂家注册表（PLT-CAM-02）。本机偏好存 localStorage。 */

export type CameraVendorId = 'eloam' | 'generic_ws'

export type CameraVendorDef = {
  id: CameraVendorId
  label: string
  /** 本机 WebSocket 地址；空表示未实现 */
  wsUrl: string
  /** 是否已实现驱动适配 */
  implemented: boolean
  hint: string
}

export const CAMERA_VENDORS: CameraVendorDef[] = [
  {
    id: 'eloam',
    label: '新良田（Eloam）',
    wsUrl: 'ws://127.0.0.1:9000',
    implemented: true,
    hint: '需安装 Eloam/新良田本机服务，默认端口 9000'
  },
  {
    id: 'generic_ws',
    label: '其他品牌（预留）',
    wsUrl: '',
    implemented: false,
    hint: '协议待对接；可先在调试页查看说明'
  }
]

const STORAGE_KEY = 'meis.camera.vendorId'

export function getCameraVendor(id: string | null | undefined): CameraVendorDef {
  return CAMERA_VENDORS.find((v) => v.id === id) || CAMERA_VENDORS[0]!
}

export function loadPreferredVendorId(): CameraVendorId {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw && CAMERA_VENDORS.some((v) => v.id === raw)) return raw as CameraVendorId
  } catch {
    /* ignore */
  }
  return 'eloam'
}

export function savePreferredVendorId(id: CameraVendorId) {
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    /* ignore */
  }
}
