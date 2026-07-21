import { SUBSCRIBE_TMPL, type SubscribeScene } from '@/config/subscribe'

function tmplIdsFor(scene: SubscribeScene): string[] {
  const ids: string[] = []
  if (scene === 'repair_submit' || scene === 'login') {
    if (SUBSCRIBE_TMPL.workorderStatus) ids.push(SUBSCRIBE_TMPL.workorderStatus)
  }
  if (scene === 'ops_complete' || scene === 'login') {
    if (SUBSCRIBE_TMPL.opsDue) ids.push(SUBSCRIBE_TMPL.opsDue)
  }
  return [...new Set(ids.filter(Boolean))]
}

/**
 * 向用户申请订阅消息授权（需用户点击手势触发）。
 * 模板未配置时静默跳过。
 */
export function requestSubscribe(scene: SubscribeScene): Promise<void> {
  const tmplIds = tmplIdsFor(scene)
  if (!tmplIds.length) return Promise.resolve()

  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    uni.requestSubscribeMessage({
      tmplIds,
      success: (res) => {
        try {
          uni.setStorageSync('meis_mp_subscribe', {
            at: Date.now(),
            scene,
            result: res
          })
        } catch {
          /* ignore */
        }
        resolve()
      },
      fail: () => resolve()
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve()
    // #endif
  })
}
