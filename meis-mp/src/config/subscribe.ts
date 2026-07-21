/**
 * 微信小程序订阅消息模板 ID（在微信公众平台 → 订阅消息 中申请后填入）。
 * 留空则跳过 requestSubscribeMessage（开发期无报错）。
 */
export const SUBSCRIBE_TMPL = {
  /** 工单状态变更（报修提交后 / 验收结果） */
  workorderStatus: '',
  /** 运维到期提醒（保养/巡检/PM） */
  opsDue: ''
} as const

export type SubscribeScene = 'repair_submit' | 'ops_complete' | 'login'
