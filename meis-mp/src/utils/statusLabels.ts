/**
 * 业务状态码 → 中文（与 sys_dict / Web i18n catalog 对齐）。
 * I18N 预留：后续可按 locale 切换。PLT-STATUS-CN-01 / §5.13。
 */

export type AppLocale = 'zh-CN'
export const DEFAULT_LOCALE: AppLocale = 'zh-CN'

const ZH: Record<string, Record<string, string>> = {
  device_status: {
    normal: '正常',
    in_use: '在用',
    maintenance: '维修中',
    scrap: '已报废',
    pending_verify: '已维修待验收',
    returned: '已退货',
    idle: '闲置',
    borrowed: '借出中'
  },
  wo_status: {
    draft: '未提交',
    reported: '报修中',
    dispatching: '派单中',
    pending_accept: '待接单',
    accepted: '已接单',
    repairing: '维修中',
    pending_verify: '已维修待验收',
    verify_rejected: '拒绝验收',
    verified: '已验收',
    closed: '已关闭',
    cancelled: '已取消',
    suspended: '已挂起'
  },
  loan_status: {
    draft: '草稿',
    pending: '待审批',
    approved: '已审批',
    on_loan: '借出中',
    returned: '已归还',
    rejected: '已驳回'
  },
  return_status: {
    draft: '草稿',
    pending: '待审批',
    approved: '已审批',
    rejected: '已驳回',
    returned: '已退库'
  },
  check_status: {
    planning: '计划中',
    in_progress: '盘点中',
    completed: '已完成'
  },
  audit_status: {
    pending: '待审核',
    approved: '已审核',
    rejected: '已驳回'
  },
  check_type: {
    annual: '年度盘点',
    spot: '抽盘',
    dept: '科室盘点'
  },
  scrap_status: {
    draft: '草稿',
    pending: '审批中',
    approved: '已批准',
    disposed: '已处置',
    rejected: '已驳回'
  },
  eq_class: {
    class_1: 'Ⅰ类',
    class_2: 'Ⅱ类',
    class_3: 'Ⅲ类'
  },
  device_criticality: {
    high: '高',
    medium: '中',
    low: '低'
  },
  acquisition_mode: {
    purchase: '购入',
    donation: '捐赠',
    transfer_in: '划拨',
    finance_lease: '融资租赁',
    other: '其他'
  },
  depreciation_method: {
    straight_line: '平均年限法',
    units_of_production: '工作量法',
    other: '其他'
  },
  device_license_type: {
    registration: '注册证',
    metrology: '计量证书',
    special: '特种许可',
    inspection: '强检证书',
    other: '其他'
  },
  auth_scope: {
    operate: '操作授权',
    maintain: '维保授权'
  },
  udi_change_reason: {
    update: '更新',
    correct: '纠错',
    import: '导入'
  },
  service_expiry_basis: {
    enable: '启用日期+年限',
    production: '生产日期+年限',
    acceptance: '验收日期+年限',
    purchase: '购置日期+年限',
    created: '录入日期+年限'
  },
  maintain_exec_status: {
    draft: '草稿',
    pending: '待执行',
    in_progress: '执行中',
    submitted: '已提交',
    audited: '已审核',
    completed: '已完成'
  },
  inspect_exec_status: {
    draft: '草稿',
    pending: '待执行',
    in_progress: '执行中',
    submitted: '已提交',
    audited: '已审核',
    completed: '已完成'
  },
  maintain_exec_item_status: {
    pending: '待执行',
    in_progress: '执行中',
    completed: '已完成',
    confirmed: '已确认'
  },
  inspect_exec_item_status: {
    pending: '待执行',
    in_progress: '执行中',
    completed: '已完成',
    confirmed: '已确认'
  },
  execution_channel: {
    web: 'Web',
    app: 'App',
    mp: '小程序'
  }
}

export function resolveStatusLabel(
  dictType: string | undefined,
  value: unknown,
  locale: AppLocale = DEFAULT_LOCALE
): string {
  if (value === null || value === undefined || value === '') return '—'
  const code = String(value)
  if (/[\u4e00-\u9fff]/.test(code)) return code
  const hit = locale === 'zh-CN' && dictType ? ZH[dictType]?.[code] : undefined
  if (hit) return hit
  return `未知(${code})`
}

export function opsExecStatusLabel(value: unknown) {
  return resolveStatusLabel('maintain_exec_status', value)
}

export function opsExecItemStatusLabel(value: unknown) {
  return resolveStatusLabel('maintain_exec_item_status', value)
}
