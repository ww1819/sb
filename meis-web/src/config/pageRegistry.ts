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
    listFilters: [
      { key: 'approval_status', label: '审批状态', dictType: 'approval_status' },
      { key: 'plan_year', label: '计划年度', type: 'number' }
    ]
  },
  '/purchase/project': {
    title: '采购项目',
    apiBase: '/purchase',
    table: 'purchase_project',
    listPageUrl: '/purchase/project/page',
    listFilters: [{ key: 'status', label: '项目状态', dictType: 'project_status' }]
  },
  '/purchase/contract': {
    title: '采购合同',
    apiBase: '/purchase',
    table: 'purchase_contract',
    listPageUrl: '/purchase/contract/page',
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
  '/purchase/supplier': { title: '供应商管理', apiBase: '/system', table: 'supplier' },
  '/purchase/category': { title: '设备分类', apiBase: '/system', table: 'medical_device_category' },
  '/purchase/manufacturer': { title: '生产厂商', apiBase: '/system', table: 'manufacturer' },
  '/purchase/dashboard': { title: '采购看板', apiBase: '/purchase', table: 'purchase_plan' },
  '/purchase/trace': { title: '业务追溯', apiBase: '/purchase', table: 'purchase_plan' },
  '/asset/device': { title: '设备台账', apiBase: '/asset', table: 'medical_device' },
  '/asset/entry': {
    title: '设备入库',
    apiBase: '/asset',
    table: 'device_entry',
    masterDetail: true,
    detailTable: 'device_entry_item',
    foreignKey: 'entry_id',
    listPageUrl: '/asset/entry/page',
    listFilters: [{ key: 'status', label: '状态', dictType: 'entry_status' }]
  },
  '/asset/outbound': { title: '设备出库', apiBase: '/asset', table: 'device_outbound', masterDetail: true, detailTable: 'device_outbound_item', foreignKey: 'outbound_id' },
  '/asset/transfer': { title: '资产流转', apiBase: '/asset', table: 'asset_transfer' },
  '/asset/inventory': { title: '资产盘点', apiBase: '/asset', table: 'inventory_check', masterDetail: true, detailTable: 'inventory_check_item', foreignKey: 'check_id' },
  '/asset/scrap': { title: '设备报废', apiBase: '/asset', table: 'device_scrap' },
  '/asset/inspection': { title: '设备巡检', apiBase: '/asset', table: 'inspection_plan' },
  '/repair/workorder': { title: '维修工单', apiBase: '/repair', table: 'repair_workorder' },
  '/repair/engineer': { title: '工程师', apiBase: '/repair', table: 'engineer' },
  '/repair/spare': { title: '备件管理', apiBase: '/repair', table: 'spare_part' },
  '/repair/fault': { title: '故障库', apiBase: '/repair', table: 'fault_type_dict' },
  '/maintain/template': { title: '保养模板', apiBase: '/maintain', table: 'maintenance_template' },
  '/maintain/plan': { title: '保养计划', apiBase: '/maintain', table: 'maintenance_plan' },
  '/maintain/record': { title: '保养记录', apiBase: '/maintain', table: 'maintenance_record' },
  '/qc/risk': { title: '风险评估', apiBase: '/qc', table: 'risk_assessment' },
  '/qc/adverse': { title: '不良事件', apiBase: '/qc', table: 'adverse_event' },
  '/qc/metrology': { title: '计量管理', apiBase: '/qc', table: 'metrology_record' },
  '/qc/performance': { title: '性能检测', apiBase: '/qc', table: 'performance_test' },
  '/maintenance-contract/list': { title: '维保合同', apiBase: '/maintenance-contract', table: 'maintenance_contract' },
  '/maintenance-contract/fulfillment': { title: '履约记录', apiBase: '/maintenance-contract', table: 'maintenance_contract_fulfillment' },
  '/special/life': { title: '生命支持', apiBase: '/special', table: 'life_support_device' },
  '/special/emergency': { title: '应急设备', apiBase: '/special', table: 'emergency_device_pool' },
  '/special/leased': { title: '租赁设备', apiBase: '/special', table: 'leased_device' },
  '/system/campus': { title: '院区管理', apiBase: '/system', table: 'campus' },
  '/system/dept': { title: '科室管理', apiBase: '/system', table: 'department' },
  '/system/user': { title: '用户管理', apiBase: '/system', table: 'sys_user' },
  '/system/role': { title: '角色管理', apiBase: '/system', table: 'sys_role' },
  '/system/dict': { title: '数据字典', apiBase: '/system', table: 'sys_dict' },
  '/system/log': { title: '操作日志', apiBase: '/system', table: 'sys_operation_log' },
  '/system/approval': { title: '审批配置', apiBase: '/system', table: 'sys_approval_flow' }
}

export function getPageConfig(path: string): PageConfig | undefined {
  return pageRegistry[path]
}
