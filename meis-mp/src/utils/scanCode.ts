/**
 * 小程序统一扫码（MOB-SCAN-04）。
 * 成功返回 trim 后的条码；取消返回 null（不 toast）；失败 toast 中文提示。
 */
export function scanBarcode(options?: { onlyFromCamera?: boolean }): Promise<string | null> {
  return new Promise((resolve) => {
    uni.scanCode({
      onlyFromCamera: options?.onlyFromCamera ?? false,
      success: (res) => {
        const code = String(res.result || '').trim()
        if (!code) {
          uni.showToast({ title: '未识别到有效条码', icon: 'none' })
          resolve(null)
          return
        }
        resolve(code)
      },
      fail: (err) => {
        const msg = String((err as { errMsg?: string })?.errMsg || '')
        // 用户主动取消不打扰
        if (/cancel|取消/i.test(msg)) {
          resolve(null)
          return
        }
        uni.showToast({
          title: '扫码失败，请重试或在设置中开启相机权限',
          icon: 'none'
        })
        resolve(null)
      }
    })
  })
}
