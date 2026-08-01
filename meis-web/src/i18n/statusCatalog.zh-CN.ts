/**
 * 业务码 → 中文静态目录（与 sys_dict 种子对齐，作字典未加载/离线兜底）。
 * 新增枚举须同步 R__data_fix.sql 与本表。PLT-STATUS-CN-01 / §5.13。
 */
import type { AppLocale } from './types'

/** prop → 默认 dictType（自定义页未声明 dictType 时） */
export const PROP_TO_DICT_TYPE: Record<string, string> = {
  device_status: 'device_status',
  existing_device_status: 'device_status',
  approval_status: 'approval_status',
  acceptance_status: 'acceptance_status',
  entry_status: 'entry_status',
  outbound_status: 'outbound_status',
  transfer_status: 'transfer_status',
  check_status: 'check_status',
  audit_status: 'audit_status',
  condition_status: 'condition_status',
  scrap_status: 'scrap_status',
  payment_status: 'payment_status',
  project_status: 'project_status',
  contract_status: 'contract_status',
  wo_status: 'wo_status',
  repair_sub_status: 'repair_sub_status',
  loan_status: 'loan_status',
  return_status: 'return_status',
  goods_return_status: 'goods_return_status',
  adverse_status: 'adverse_status',
  depreciation_status: 'depreciation_status',
  bidding_status: 'bidding_status',
  urgency: 'urgency',
  risk_level: 'risk_level',
  status: '', // 需显式 dictType，勿盲目推断
}

const ZH: Record<string, Record<string, string>> = {
  device_status: {
    normal: '正常',
    in_use: '在用',
    maintenance: '维修中',
    scrap: '已报废',
    pending_verify: '已维修待验收',
    returned: '已退货',
    idle: '闲置',
    borrowed: '借出中',
  },
  approval_status: {
    draft: '未提交',
    pending: '审批中',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
  },
  maintain_approval_status: {
    draft: '未提交',
    pending: '审批中',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
  },
  inspect_approval_status: {
    draft: '未提交',
    pending: '审批中',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
  },
  metrology_approval_status: {
    draft: '未提交',
    pending: '审批中',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
  },
  contract_approval_status: {
    draft: '未审批',
    pending: '未审批',
    rejected: '未审批',
    unapproved: '未审批',
    approved: '已审批',
  },
  acceptance_review_status: {
    draft: '未审核',
    pending: '未审核',
    rejected: '未审核',
    approved: '已审核',
  },
  acceptance_status: {
    pending: '待验收',
    passed: '已经验收',
    failed: '验收不通过',
  },
  entry_status: {
    draft: '草稿',
    pending: '待验收',
    completed: '已完成',
    approved: '已审批',
  },
  outbound_status: {
    draft: '草稿',
    issued: '已发放',
    approved: '已审批',
    pending: '待审批',
  },
  transfer_status: {
    draft: '草稿',
    pending: '待审批',
    approved: '已批准',
    completed: '已完成',
    rejected: '已驳回',
  },
  check_status: {
    planning: '计划中',
    in_progress: '盘点中',
    completed: '已完成',
  },
  audit_status: {
    pending: '待审核',
    approved: '已审核',
    rejected: '已驳回',
  },
  condition_status: {
    good: '良好',
    fair: '一般',
    poor: '较差',
    normal: '正常',
  },
  scrap_status: {
    draft: '草稿',
    pending: '审批中',
    approved: '已批准',
    disposed: '已处置',
    rejected: '已驳回',
  },
  payment_status: {
    pending: '待付款',
    paid: '已付款',
  },
  project_status: {
    draft: '草稿',
    bidding: '招标中',
    awarded: '已定标',
    closed: '已关闭',
  },
  contract_status: {
    active: '生效',
    completed: '已完成',
    terminated: '已终止',
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
    suspended: '已挂起',
  },
  repair_sub_status: {
    internal: '院内维修',
    external: '院外维修',
    waiting_parts: '等待配件',
    waiting_approval: '待审批',
    on_site: '已到场',
    diagnosing: '诊断中',
    testing: '调试中',
    verified: '已验收',
  },
  loan_status: {
    draft: '草稿',
    pending: '待审批',
    approved: '已审批',
    on_loan: '借出中',
    returned: '已归还',
    rejected: '已驳回',
  },
  return_status: {
    draft: '草稿',
    pending: '待审批',
    approved: '已审批',
    rejected: '已驳回',
    returned: '已退库',
  },
  goods_return_status: {
    draft: '草稿',
    pending: '待审批',
    approved: '已审批',
    returned: '已退货',
    rejected: '已驳回',
  },
  adverse_status: {
    reported: '已上报',
    handling: '处理中',
    reviewed: '已审核',
    closed: '已结案',
  },
  depreciation_status: {
    not_started: '未开始',
    depreciating: '折旧中',
    completed: '已提足',
    suspended: '暂停折旧',
  },
  urgency: {
    urgent: '紧急',
    high: '高',
    normal: '普通',
    low: '低',
  },
  risk_level: {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
  },
  bidding_status: {
    passed: '已招标',
    pending: '未招标',
    failed: '未招标',
    not_passed: '未招标',
    已招标: '已招标',
    未招标: '未招标',
  },
  maintain_exec_status: {
    draft: '草稿',
    pending: '待执行',
    in_progress: '执行中',
    submitted: '已提交',
    audited: '已审核',
    completed: '已完成',
  },
  inspect_exec_status: {
    draft: '草稿',
    pending: '待执行',
    in_progress: '执行中',
    submitted: '已提交',
    audited: '已审核',
    completed: '已完成',
  },
  metrology_exec_status: {
    draft: '草稿',
    pending: '待执行',
    in_progress: '执行中',
    submitted: '已提交',
    audited: '已审核',
    completed: '已完成',
  },
  maintain_exec_item_status: {
    pending: '待执行',
    in_progress: '执行中',
    completed: '已完成',
    confirmed: '已确认',
  },
  inspect_exec_item_status: {
    pending: '待执行',
    in_progress: '执行中',
    completed: '已完成',
    confirmed: '已确认',
  },
  metrology_exec_item_status: {
    pending: '待执行',
    in_progress: '执行中',
    completed: '已完成',
    confirmed: '已确认',
  },
  plan_status: {
    draft: '草稿',
    active: '进行中',
    completed: '已完成',
    cancelled: '已取消',
    pending: '待开始',
    paused: '已暂停',
  },
  maintain_plan_status: {
    active: '激活',
    paused: '暂停',
    completed: '完成',
  },
  pm_exec_status: {
    draft: '草稿',
    pending: '待执行',
    in_progress: '执行中',
    submitted: '已提交',
    audited: '已审核',
    completed: '已完成',
  },
  pm_exec_item_status: {
    pending: '待执行',
    in_progress: '执行中',
    completed: '已完成',
    confirmed: '已确认',
  },
  metrology_result: {
    qualified: '合格',
    unqualified: '不合格',
    pass: '合格',
    fail: '不合格',
    pending: '待判定',
  },
  inspect_result: {
    qualified: '合格',
    unqualified: '不合格',
    pass: '合格',
    fail: '不合格',
    pending: '待判定',
  },
  maintain_result: {
    pass: '合格',
    fail: '不合格',
    qualified: '合格',
    unqualified: '不合格',
  },
  ops_result_status: {
    pending: '待填',
    pass: '合格',
    fail: '不合格',
    na: '不适用',
  },
  source_mode: {
    biz_doc: '业务单据',
    biz_op: '业务操作写回',
    manual_backfill: '手工补录',
    system: '系统生成',
    manual_transfer: '手工真变更',
    external: '外部/财务',
  },
  confirm_status: {
    draft: '待确认',
    confirmed: '已确认',
  },
  owner_type: {
    warehouse: '仓库',
    dept: '科室',
  },
  ownership_change_reason: {
    biz_doc: '业务单据',
    manual_transfer: '手工真变更',
    manual_correct: '手工纠错',
    manual_backfill: '手工补录',
    import: '导入',
  },
  disposition_biz_status: {
    draft: '草稿',
    pending: '待处理',
    approved: '已批准',
    issued: '已发放',
    returned: '已退回',
    completed: '已完成',
    disposed: '已处置',
    confirmed: '已确认',
    planning: '计划中',
    in_progress: '进行中',
    rejected: '已驳回',
    synced: '已同步',
    failed: '同步失败',
    ignored: '已忽略',
  },
  sync_status: {
    pending: '待同步',
    synced: '已同步',
    failed: '同步失败',
    ignored: '已忽略',
  },
  udi_change_reason: {
    update: '更新',
    correct: '纠错',
    import: '导入',
  },
  auth_scope: {
    operate: '操作授权',
    maintain: '维保授权',
  },
  paid_status: {
    unpaid: '未收费',
    paid: '已收费',
    waived: '已减免',
  },
  shared_fee_mode: {
    per_use: '按次收费',
    time: '计时收费',
  },
  check_type: {
    annual: '年度盘点',
    spot: '抽盘',
    dept: '科室盘点',
  },
  eq_class: {
    class_1: 'Ⅰ类',
    class_2: 'Ⅱ类',
    class_3: 'Ⅲ类',
  },
  device_criticality: {
    high: '高',
    medium: '中',
    low: '低',
  },
  acquisition_mode: {
    purchase: '购入',
    donation: '捐赠',
    transfer_in: '划拨',
    finance_lease: '融资租赁',
    other: '其他',
  },
  depreciation_method: {
    straight_line: '平均年限法',
    units_of_production: '工作量法',
    other: '其他',
  },
  device_license_type: {
    registration: '注册证',
    metrology: '计量证书',
    special: '特种许可',
    inspection: '强检证书',
    other: '其他',
  },
  service_expiry_basis: {
    enable: '启用日期+年限',
    production: '生产日期+年限',
    acceptance: '验收日期+年限',
    purchase: '购置日期+年限',
    created: '录入日期+年限',
  },
  inspection_status: {
    pending: '待巡检',
    completed: '已完成',
    in_progress: '巡检中',
  },
  service_health: {
    UP: '正常',
    DOWN: '异常',
    up: '正常',
    down: '异常',
  },
  tenant_status: {
    active: '启用',
    inactive: '停用',
    disabled: '停用',
    suspended: '已挂起',
  },
  execution_channel: {
    web: 'Web',
    app: 'App',
    mp: '小程序',
  },
}

const CATALOGS: Record<AppLocale, Record<string, Record<string, string>>> = {
  'zh-CN': ZH,
}

export function lookupStatusCatalog(
  dictType: string | undefined,
  value: unknown,
  locale: AppLocale = 'zh-CN'
): string | null {
  if (!dictType || value === null || value === undefined || value === '') return null
  const code = String(value)
  const hit = CATALOGS[locale]?.[dictType]?.[code]
  return hit ?? null
}

export function inferDictType(prop?: string, dictType?: string): string | undefined {
  if (dictType) return dictType
  if (!prop) return undefined
  return PROP_TO_DICT_TYPE[prop] || undefined
}
