export interface ListFilter {
  key: string
  label: string
  dictType?: string
  type?: 'select' | 'number'
}

export interface PageConfig {
  title: string
  apiBase: string
  table: string
  columns?: string[]
  masterDetail?: boolean
  detailTable?: string
  foreignKey?: string
  listPageUrl?: string
  listFilters?: ListFilter[]
  importable?: boolean
  importUrl?: string
  importTemplateUrl?: string
  pinyinCode?: boolean
  pinyinCodeUrl?: string
  exportUrl?: string
  /** 列表分页接口附加模式：apply / handle / verify */
  listMode?: string
  /** 列表分页固定查询参数 */
  listParams?: Record<string, string | number | boolean>
  /** 保存接口（POST，支持新增/编辑合一） */
  saveUrl?: string
  /** 启用查看（只读） */
  enableView?: boolean
  /** 是否提供变更记录（默认跟随 enableView） */
  enableChangeLog?: boolean
}

export const pageRegistry: Record<string, PageConfig> = {
  '/purchase/plan': {
    title: '采购计划',
    apiBase: '/purchase',
    table: 'purchase_plan',
    masterDetail: true,
    detailTable: 'purchase_plan_item',
    foreignKey: 'plan_id',
    listPageUrl: '/purchase/plan/page',
    saveUrl: '/purchase/plan',
    listFilters: [
      { key: 'approval_status', label: '审批状态', dictType: 'approval_status' },
      { key: 'plan_year', label: '计划年度', type: 'number' }
    ]
  },
  '/purchase/apply': {
    title: '采购申请',
    apiBase: '/purchase',
    table: 'purchase_plan',
    masterDetail: true,
    detailTable: 'purchase_plan_item',
    foreignKey: 'plan_id',
    listPageUrl: '/purchase/plan/page',
    saveUrl: '/purchase/plan',
    listFilters: [
      { key: 'approval_status', label: '审批状态', dictType: 'approval_status' },
      { key: 'plan_year', label: '计划年度', type: 'number' }
    ]
  },
  '/purchase/approval': {
    title: '采购审批',
    apiBase: '/purchase',
    table: 'sys_approval_instance',
    listPageUrl: '/purchase/approval/page'
  },
  '/purchase/project': {
    title: '采购项目',
    apiBase: '/purchase',
    table: 'purchase_project',
    listPageUrl: '/purchase/project/page',
    saveUrl: '/purchase/project',
    listFilters: [{ key: 'status', label: '项目状态', dictType: 'project_status' }]
  },
  '/purchase/contract': {
    title: '设备合同管理',
    apiBase: '/purchase',
    table: 'purchase_contract',
    listPageUrl: '/purchase/contract/page',
    saveUrl: '/purchase/contract',
    listFilters: [
      { key: 'approval_status', label: '审批状态', dictType: 'approval_status' },
      { key: 'acceptance_status', label: '验收状态', dictType: 'acceptance_status' }
    ]
  },
  '/purchase/acceptance': {
    title: '安装验收',
    apiBase: '/purchase',
    table: 'purchase_acceptance',
    listPageUrl: '/purchase/acceptance/page',
    listFilters: [{ key: 'acceptance_status', label: '验收状态', dictType: 'acceptance_status' }]
  },
  '/purchase/supplier': { title: '供应商管理', apiBase: '/system', table: 'supplier', importable: true, pinyinCode: true,
  enableView: true
},
  '/purchase/category': { title: '设备分类', apiBase: '/system', table: 'medical_device_category',
  enableView: true
},
  '/purchase/manufacturer': { title: '生产厂商', apiBase: '/system', table: 'manufacturer', importable: true, pinyinCode: true,
  enableView: true
},
  '/dict/supplier': { title: '供应商维护', apiBase: '/system', table: 'supplier', importable: true, pinyinCode: true,
  enableView: true
},
  '/dict/manufacturer': { title: '生产厂家维护', apiBase: '/system', table: 'manufacturer', importable: true, pinyinCode: true,
  enableView: true
},
  '/dict/category': { title: '设备68档案', apiBase: '/system', table: 'medical_device_category',
  enableView: true
},
  '/dict/asset-category': { title: '资产分类', apiBase: '/system', table: 'asset_category',
  enableView: true
},
  '/dict/finance-category': { title: '财务分类', apiBase: '/system', table: 'finance_category',
  enableView: true
},
  '/dict/dept': { title: '科室维护', apiBase: '/system', table: 'department', pinyinCode: true,
  enableView: true
},
  '/dict/warehouse': { title: '仓库维护', apiBase: '/system', table: 'warehouse',
  enableView: true
},
  '/dict/unit': { title: '单位维护', apiBase: '/system', table: 'unit_dict',
  enableView: true
},
  '/purchase/dashboard': { title: '采购看板', apiBase: '/purchase', table: 'purchase_plan' },
  '/purchase/trace': { title: '业务追溯', apiBase: '/purchase', table: 'purchase_plan' },
  '/asset/query': { title: '资产综合查询', apiBase: '/asset', table: 'medical_device' },
  '/asset/import': {
    title: '资产导入',
    apiBase: '/asset',
    table: 'medical_device',
    importable: true,
    importUrl: '/asset/medical_device/import',
    importTemplateUrl: '/asset/medical_device/import/template'
  },
  '/asset/device': {
    title: '资产管理',
    apiBase: '/asset',
    table: 'medical_device',
    listPageUrl: '/asset/device/page',
  enableView: true
},
  '/asset/entry': {
    title: '设备入库',
    apiBase: '/asset',
    table: 'device_entry',
    masterDetail: true,
    detailTable: 'device_entry_item',
    foreignKey: 'entry_id',
    listPageUrl: '/asset/entry/page',
    saveUrl: '/asset/entry',
    listFilters: [{ key: 'status', label: '状态', dictType: 'entry_status' }]
  },
  '/asset/outbound': {
    title: '设备出库',
    apiBase: '/asset',
    table: 'device_outbound',
    masterDetail: true,
    detailTable: 'device_outbound_item',
    foreignKey: 'outbound_id',
    listPageUrl: '/asset/outbound/page',
    saveUrl: '/asset/outbound',
    listFilters: [{ key: 'doc_status', label: '审批状态', dictType: 'approval_status' }]
  },
  '/asset/transfer': { title: '资产流转', apiBase: '/asset', table: 'asset_transfer', saveUrl: '/asset/transfer' },
  '/asset/inventory': {
    title: '资产盘点',
    apiBase: '/asset',
    table: 'inventory_check',
    masterDetail: true,
    detailTable: 'inventory_check_item',
    foreignKey: 'check_id',
    listPageUrl: '/asset/inventory/page',
    saveUrl: '/asset/inventory',
    listFilters: [{ key: 'audit_status', label: '审核状态', dictType: 'audit_status' }]
  },
  '/asset/scrap': { title: '设备报废', apiBase: '/asset', table: 'device_scrap', saveUrl: '/asset/scrap' },
  '/warehouse/setting': { title: '库房维护', apiBase: '/system', table: 'warehouse',
  enableView: true
},
  '/warehouse/entry': {
    title: '设备入库',
    apiBase: '/asset',
    table: 'device_entry',
    masterDetail: true,
    detailTable: 'device_entry_item',
    foreignKey: 'entry_id',
    listPageUrl: '/asset/entry/page',
    saveUrl: '/asset/entry',
    listFilters: [{ key: 'status', label: '状态', dictType: 'entry_status' }]
  },
  '/warehouse/outbound': {
    title: '设备出库',
    apiBase: '/asset',
    table: 'device_outbound',
    masterDetail: true,
    detailTable: 'device_outbound_item',
    foreignKey: 'outbound_id',
    listPageUrl: '/asset/outbound/page',
    saveUrl: '/asset/outbound',
    listFilters: [{ key: 'doc_status', label: '审批状态', dictType: 'approval_status' }]
  },
  '/warehouse/return': {
    title: '设备退货',
    apiBase: '/asset',
    table: 'device_return',
    masterDetail: true,
    detailTable: 'device_return_item',
    foreignKey: 'return_id',
    listPageUrl: '/asset/return/page',
    saveUrl: '/asset/return',
    listFilters: [{ key: 'status', label: '状态', dictType: 'return_status' }]
  },
  '/warehouse/transfer': { title: '库房调拨', apiBase: '/asset', table: 'asset_transfer', saveUrl: '/asset/transfer' },
  '/warehouse/inventory': {
    title: '库存盘点',
    apiBase: '/asset',
    table: 'inventory_check',
    masterDetail: true,
    detailTable: 'inventory_check_item',
    foreignKey: 'check_id',
    listPageUrl: '/asset/inventory/page',
    saveUrl: '/asset/inventory',
    listFilters: [{ key: 'audit_status', label: '审核状态', dictType: 'audit_status' }]
  },
  '/warehouse/scrap': { title: '设备报废', apiBase: '/asset', table: 'device_scrap', saveUrl: '/asset/scrap' },
  '/asset/inspection': { title: '设备巡检', apiBase: '/asset', table: 'inspection_plan' },
  '/repair/apply': {
    title: '报修申请',
    apiBase: '/repair',
    table: 'repair_workorder',
    listPageUrl: '/repair/workorder/page',
    listMode: 'apply'
  },
  '/repair/handle': {
    title: '维修处理',
    apiBase: '/repair',
    table: 'repair_workorder',
    listPageUrl: '/repair/workorder/page',
    listMode: 'handle'
  },
  '/repair/verify': {
    title: '维修验收',
    apiBase: '/repair',
    table: 'repair_workorder',
    listPageUrl: '/repair/workorder/page',
    listMode: 'verify'
  },
  '/repair/workorder': {
    title: '维修工单',
    apiBase: '/repair',
    table: 'repair_workorder',
    listPageUrl: '/repair/workorder/page',
    saveUrl: '/repair/workorder',
    listFilters: [{ key: 'status', label: '状态', dictType: 'wo_status' }]
  },
  '/repair/engineer': { title: '工程师', apiBase: '/repair', table: 'engineer',
  enableView: true
},
  '/repair/spare-archive': { title: '配件档案管理', apiBase: '/repair', table: 'spare_part' },
  '/repair/fault': { title: '故障库', apiBase: '/repair', table: 'fault_type_dict',
  enableView: true
},
  '/maintain/param': { title: '保养参数设置', apiBase: '/maintain', table: 'maintenance_level' },
  '/maintain/plan': { title: '保养计划', apiBase: '/maintain', table: 'maintenance_plan', saveUrl: '/maintain/plan' },
  '/maintain/execution': {
    title: '保养执行',
    apiBase: '/maintain',
    table: 'maintenance_execution',
    listPageUrl: '/maintain/execution/page'
  },
  '/maintain/query': {
    title: '保养记录查询',
    apiBase: '/maintain',
    table: 'maintenance_execution_item',
    listPageUrl: '/maintain/query/page'
  },
  '/maintain/template': { title: '保养模板', apiBase: '/maintain', table: 'maintenance_template', saveUrl: '/maintain/template' },
  '/maintain/record': { title: '保养记录', apiBase: '/maintain', table: 'maintenance_record', saveUrl: '/maintain/record' },
  '/inspect/param': { title: '巡检参数设置', apiBase: '/inspect', table: 'inspection_type' },
  '/inspect/plan': { title: '巡检计划', apiBase: '/inspect', table: 'inspection_plan', saveUrl: '/inspect/plan' },
  '/inspect/execution': {
    title: '巡检执行',
    apiBase: '/inspect',
    table: 'inspection_execution',
    listPageUrl: '/inspect/execution/page'
  },
  '/inspect/query': {
    title: '巡检记录查询',
    apiBase: '/inspect',
    table: 'inspection_execution_item',
    listPageUrl: '/inspect/query/page'
  },
  '/metrology/param': { title: '计量参数设置', apiBase: '/metrology', table: 'metrology_category' },
  '/metrology/plan': { title: '计量计划', apiBase: '/metrology', table: 'metrology_plan', saveUrl: '/metrology/plan' },
  '/metrology/execution': {
    title: '计量执行',
    apiBase: '/metrology',
    table: 'metrology_execution',
    listPageUrl: '/metrology/execution/page'
  },
  '/metrology/query': {
    title: '计量记录查询',
    apiBase: '/metrology',
    table: 'metrology_execution_item',
    listPageUrl: '/metrology/query/page'
  },
  '/qc/risk': { title: '风险评估', apiBase: '/qc', table: 'risk_assessment' },
  '/qc/adverse': { title: '不良事件', apiBase: '/qc', table: 'adverse_event' },
  '/qc/adverse/report': {
    title: '不良事件上报',
    apiBase: '/qc',
    table: 'adverse_event',
    listPageUrl: '/qc/adverse/page',
    saveUrl: '/qc/adverse',
    listParams: { openOnly: true },
    listFilters: [
      { key: 'status', label: '状态', dictType: 'adverse_status' },
      { key: 'severityLevel', label: '严重等级', dictType: 'adverse_severity' }
    ]
  },
  '/qc/adverse/query': {
    title: '不良事件查询',
    apiBase: '/qc',
    table: 'adverse_event',
    listPageUrl: '/qc/adverse/page',
    listFilters: [
      { key: 'status', label: '状态', dictType: 'adverse_status' },
      { key: 'severityLevel', label: '严重等级', dictType: 'adverse_severity' },
      { key: 'eventType', label: '事件类型', dictType: 'adverse_event_type' }
    ]
  },
  '/qc/metrology': { title: '计量管理', apiBase: '/qc', table: 'metrology_record' },
  '/qc/performance': { title: '性能检测', apiBase: '/qc', table: 'performance_test' },
  '/maintenance-contract/list': { title: '维保合同', apiBase: '/maintenance-contract', table: 'maintenance_contract' },
  '/maintenance-contract/fulfillment': { title: '履约记录', apiBase: '/maintenance-contract', table: 'maintenance_contract_fulfillment' },
  '/special/life': {
    title: '生命支持设备',
    apiBase: '/special',
    table: 'life_support_device',
    listPageUrl: '/special/life/page',
    saveUrl: '/special/life'
  },
  '/special/radiation': {
    title: '特种设备登记',
    apiBase: '/special',
    table: 'special_device',
    listPageUrl: '/special/radiation/page',
    saveUrl: '/special/radiation',
    listFilters: [
      { key: 'specialType', label: '特种类型', dictType: 'special_type' }
    ]
  },
  '/special/emergency': {
    title: '应急设备库',
    apiBase: '/special',
    table: 'emergency_device_pool',
    listPageUrl: '/special/emergency/page'
  },
  '/special/leased': {
    title: '租赁设备',
    apiBase: '/special',
    table: 'leased_device',
    listPageUrl: '/special/leased/page',
    listFilters: [{ key: 'status', label: '状态', dictType: 'lease_status' }]
  },
  '/special/alerts': { title: '证照到期提醒', apiBase: '/special', table: 'special_device' },
  '/shared/device': {
    title: '公用设备管理',
    apiBase: '/shared',
    table: 'shared_device',
    listPageUrl: '/shared/device/page',
    saveUrl: '/shared/device'
  },
  '/shared/loan': {
    title: '借调申请',
    apiBase: '/shared',
    table: 'shared_device_loan',
    listPageUrl: '/shared/loan/page',
    saveUrl: '/shared/loan'
  },
  '/shared/loan-approve': {
    title: '借调审批',
    apiBase: '/shared',
    table: 'shared_device_loan',
    listPageUrl: '/shared/loan/page',
    listParams: { pendingOnly: true }
  },
  '/shared/return': {
    title: '归还申请',
    apiBase: '/shared',
    table: 'shared_device_return',
    listPageUrl: '/shared/return/page',
    saveUrl: '/shared/return'
  },
  '/shared/return-approve': {
    title: '归还审批',
    apiBase: '/shared',
    table: 'shared_device_return',
    listPageUrl: '/shared/return/page',
    listParams: { pendingOnly: true }
  },
  '/shared/fee': {
    title: '借调收费',
    apiBase: '/shared',
    table: 'shared_device_fee',
    listPageUrl: '/shared/fee/page',
    saveUrl: '/shared/fee',
    listFilters: [{ key: 'paidStatus', label: '收费状态', dictType: 'paid_status' }]
  },
  '/shared/record': {
    title: '借调记录查询',
    apiBase: '/shared',
    table: 'shared_device_loan',
    listPageUrl: '/shared/record/page',
    listFilters: [{ key: 'status', label: '状态', dictType: 'loan_status' }]
  },
  '/pm/param': { title: '预防性维护参数', apiBase: '/pm', table: 'pm_type' },
  '/pm/plan': { title: '预防性维护计划', apiBase: '/pm', table: 'pm_plan', saveUrl: '/pm/plan' },
  '/pm/execution': {
    title: '预防性维护执行',
    apiBase: '/pm',
    table: 'pm_execution',
    listPageUrl: '/pm/execution/page'
  },
  '/pm/query': { title: '预防性维护记录', apiBase: '/pm', table: 'pm_execution_item' },
  '/analytics/mapping': {
    title: '对照管理',
    apiBase: '/analytics',
    table: 'benefit_mapping',
    listPageUrl: '/analytics/mapping/page',
    saveUrl: '/analytics/mapping'
  },
  '/analytics/sync': { title: '数据抓取', apiBase: '/analytics', table: 'integration_sync_task' },
  '/analytics/summary': {
    title: '效益分析汇总',
    apiBase: '/analytics',
    table: 'device_benefit_summary',
    listPageUrl: '/analytics/summary/page',
    listFilters: [{ key: 'benefitLevel', label: '效益等级', dictType: 'benefit_level' }]
  },
  '/analytics/cost': {
    title: '成本上报',
    apiBase: '/analytics',
    table: 'device_cost_record',
    listPageUrl: '/analytics/cost/page',
    saveUrl: '/analytics/cost',
    listFilters: [{ key: 'costType', label: '成本类型', dictType: 'cost_type' }]
  },
  '/analytics/device': { title: '单机效益分析', apiBase: '/analytics', table: 'medical_device', listPageUrl: '/analytics/benefit/device/page' },
  '/power/station': {
    title: '基站维护',
    apiBase: '/power',
    table: 'power_base_station',
    listPageUrl: '/power/station/page',
    saveUrl: '/power/station'
  },
  '/power/tag': {
    title: '标签维护',
    apiBase: '/power',
    table: 'power_tag',
    listPageUrl: '/power/tag/page',
    saveUrl: '/power/tag'
  },
  '/power/status': { title: '设备运行状态', apiBase: '/power', table: 'power_device_status', listPageUrl: '/power/status/page' },
  '/power/stats': { title: '设备运行统计', apiBase: '/power', table: 'power_monitor_record' },
  '/power/record': {
    title: '监测记录',
    apiBase: '/power',
    table: 'power_monitor_record',
    listPageUrl: '/power/record/page'
  },
  '/screen/equipment': { title: '设备运营大屏', apiBase: '/screen', table: 'medical_device' },
  '/system/campus': { title: '院区管理', apiBase: '/system', table: 'campus',
  enableView: true
},
  '/system/dept': { title: '科室管理', apiBase: '/system', table: 'department',
  enableView: true
},
  '/system/user': { title: '用户管理', apiBase: '/system', table: 'sys_user',
  enableView: true
},
  '/system/role': { title: '角色管理', apiBase: '/system', table: 'sys_role',
  enableView: true
},
  '/system/dict': { title: '数据字典', apiBase: '/system', table: 'sys_dict' },
  '/system/log': { title: '操作日志', apiBase: '/system', table: 'sys_operation_log' },
  '/system/approval': { title: '审批配置', apiBase: '/system', table: 'sys_approval_flow' }
}

export function getPageConfig(path: string): PageConfig | undefined {
  return pageRegistry[path]
}
