import type { FieldGroup } from './pageSchemas'

export interface ListFilterOption {
  value: string
  label: string
}

export interface MoreSearchField {
  key: string
  label: string
  placeholder?: string
  /** 关联表：输入时远程下拉联想，选中后以 ID 查询 */
  linkTable?: string
}

export interface ListFilter {
  key: string
  label: string
  dictType?: string
  type?: 'select' | 'number' | 'date' | 'daterange'
  /** 字典多选（如状态） */
  multiple?: boolean
  /** 外键下拉（RefSelect） */
  linkTable?: string
  /** 静态选项（与 dictType 二选一） */
  options?: ListFilterOption[]
  /** 字典子集：仅展示这些 value */
  dictValues?: string[]
  /** 显示在关键词搜索框之前 */
  prepend?: boolean
  /** 显示在操作栏（新增按钮前） */
  actionBar?: boolean
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
  /** 更多搜索字段（替换关键词搜索，支持组合查询） */
  moreSearchFields?: MoreSearchField[]
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
  /** 列表首列显示分页序号 */
  showRowIndex?: boolean
  /** 序号列是否固定左侧（默认 true；false 时随表格横滚，AST-UI-12） */
  rowIndexFixed?: boolean
  /** 序号列前显示多选框 */
  showRowSelection?: boolean
  /**
   * 工具栏布局：
   * - standard（默认）：导入/生成简码在搜索行，查询/重置/新增/导出在第二行（原行为）
   * - actions-row：查询/重置跟搜索框；新增/导出/导入/生成简码单独第二行（如供应商维护）
   */
  toolbarLayout?: 'standard' | 'actions-row'
  /** 表单抽屉位置：center（默认）| right（右侧贴边，不挡顶栏） */
  formPlacement?: 'center' | 'right'
  /** 支持表头升序/降序的列 prop */
  sortableColumns?: string[]
  /** 分组表单每行列数（如基本信息 5 列） */
  formGroupColumns?: Partial<Record<FieldGroup, number>>
  /** 表单抽屉标题（create/edit/view） */
  formTitles?: { create?: string; edit?: string; view?: string }
  /** 保存成功后是否保持表单抽屉打开（默认关闭） */
  keepFormOpenAfterSave?: boolean
  /** 表单抽屉尺寸（默认 lg） */
  formDrawerSize?: 'sm' | 'md' | 'lg' | 'xl'
  /**
   * 编辑/查看时按 saveUrl/{id} 拉取完整详情（含主从 items 等），
   * 避免列表行字段不全导致保存丢明细。
   */
  loadFormDetail?: boolean
  /** 隐藏新增按钮（过滤列表等） */
  hideAdd?: boolean
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
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'plan_code',
      'campus_name',
      'dept_name',
      'applicant_name',
      'fill_date',
      'total_budget',
      'plan_year',
      'plan_type',
      'approved_by_name',
      'approved_at',
      'approval_status',
      'benefit_analysis_url',
      'dept_argument_url'
    ],
    listFilters: [
      { key: 'approval_status', label: '审批状态', dictType: 'approval_status' },
      { key: 'plan_type', label: '计划类型', dictType: 'plan_type' }
    ],
    formGroupColumns: { basic: 6 }
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
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'plan_code',
      'campus_name',
      'dept_name',
      'applicant_name',
      'fill_date',
      'total_budget',
      'plan_year',
      'plan_type',
      'approved_by_name',
      'approved_at',
      'approval_status',
      'benefit_analysis_url',
      'dept_argument_url'
    ],
    listFilters: [
      { key: 'approval_status', label: '审批状态', dictType: 'approval_status' },
      { key: 'plan_type', label: '计划类型', dictType: 'plan_type' }
    ],
    formGroupColumns: { basic: 6 }
  },
  '/purchase/approval': {
    title: '采购审批',
    apiBase: '/purchase',
    table: 'sys_approval_instance',
    listPageUrl: '/purchase/approval/page'
  },
  '/purchase/project': {
    title: '设备采购计划表',
    apiBase: '/purchase',
    table: 'purchase_plan_item',
    listPageUrl: '/purchase/project/page'
  },
  '/purchase/bidding': {
    title: '招标管理',
    apiBase: '/purchase',
    table: 'purchase_plan_item',
    listPageUrl: '/purchase/bidding/page',
    showRowSelection: true
  },
  '/purchase/contract': {
    title: '设备合同管理',
    apiBase: '/purchase',
    table: 'purchase_contract',
    listPageUrl: '/purchase/contract/page',
    saveUrl: '/purchase/contract',
    showRowSelection: true,
    formGroupColumns: { basic: 4 },
    formDrawerSize: 'xl',
    loadFormDetail: true,
    formTitles: {
      create: '新增(设备购置合同)',
      edit: '编辑(设备购置合同)',
      view: '查看(设备购置合同)'
    },
    listFilters: [
      {
        key: 'approval_status',
        label: '审批状态',
        options: [
          { value: 'unapproved', label: '未审批' },
          { value: 'approved', label: '已审批' }
        ]
      },
      { key: 'acceptance_status', label: '验收状态', dictType: 'acceptance_status' }
    ]
  },
  '/purchase/acceptance': {
    title: '安装验收',
    apiBase: '/purchase',
    table: 'purchase_acceptance',
    listPageUrl: '/purchase/acceptance/page',
    saveUrl: '/purchase/acceptance',
    showRowSelection: true,
    showRowIndex: true,
    formGroupColumns: { basic: 4 },
    formDrawerSize: 'xl',
    loadFormDetail: true,
    formTitles: {
      create: '新增(设备验收)',
      edit: '编辑(设备验收)',
      view: '查看(设备验收)'
    },
    listFilters: [{ key: 'approval_status', label: '审批状态', dictType: 'acceptance_review_status' }]
  },
  '/purchase/supplier': {
    title: '供应商管理',
    apiBase: '/system',
    table: 'supplier',
    importable: true,
    pinyinCode: true,
    toolbarLayout: 'actions-row',
    formPlacement: 'right',
    showRowIndex: true,
    sortableColumns: ['supplier_code', 'supplier_name'],
    enableView: true
  },
  '/purchase/category': {
    title: '设备分类',
    apiBase: '/system',
    table: 'medical_device_category',
    formPlacement: 'right',
    showRowIndex: true,
    importable: true,
    sortableColumns: ['category_code', 'category_name', 'parent_code'],
    enableView: true
  },
  '/purchase/manufacturer': {
    title: '生产厂商',
    apiBase: '/system',
    table: 'manufacturer',
    importable: true,
    pinyinCode: true,
    toolbarLayout: 'actions-row',
    formPlacement: 'right',
    enableView: true
  },
  '/dict/supplier': {
    title: '供应商管理',
    apiBase: '/system',
    table: 'supplier',
    importable: true,
    pinyinCode: true,
    toolbarLayout: 'actions-row',
    formPlacement: 'right',
    showRowIndex: true,
    sortableColumns: ['supplier_code', 'supplier_name'],
    enableView: true
  },
  '/dict/manufacturer': {
    title: '生产厂商',
    apiBase: '/system',
    table: 'manufacturer',
    importable: true,
    pinyinCode: true,
    toolbarLayout: 'actions-row',
    formPlacement: 'right',
    enableView: true
  },
  '/dict/category': {
    title: '设备分类',
    apiBase: '/system',
    table: 'medical_device_category',
    formPlacement: 'right',
    showRowIndex: true,
    importable: true,
    sortableColumns: ['category_code', 'category_name', 'parent_code'],
    enableView: true
  },
  '/dict/campus': { title: '院区管理', apiBase: '/system', table: 'campus',
  enableView: true
},
  '/dict/asset-category': {
    title: '资产分类',
    apiBase: '/system',
    table: 'asset_category',
    formPlacement: 'right',
    showRowIndex: true,
    sortableColumns: ['category_code', 'category_name'],
    enableView: true
  },
  '/dict/finance-category': {
    title: '财务分类',
    apiBase: '/system',
    table: 'finance_category',
    formPlacement: 'right',
    showRowIndex: true,
    enableView: true
  },
  '/dict/dept': { title: '科室维护', apiBase: '/system', table: 'department', pinyinCode: true,
  enableView: true
},
  '/dict/warehouse': { title: '仓库维护', apiBase: '/system', table: 'warehouse',
  enableView: true
},
  '/dict/unit': {
    title: '单位维护',
    apiBase: '/system',
    table: 'unit_dict',
    formPlacement: 'right',
    showRowSelection: true,
    showRowIndex: true,
    sortableColumns: ['unit_code', 'unit_name'],
    enableView: true
  },
  '/purchase/dashboard': { title: '采购看板', apiBase: '/purchase', table: 'purchase_plan' },
  '/purchase/trace': { title: '业务追溯', apiBase: '/purchase', table: 'purchase_plan' },
  '/asset/query': { title: '资产综合查询', apiBase: '/asset', table: 'medical_device' },
  '/asset/dynamic-stats': { title: '资产动态统计', apiBase: '/asset', table: 'medical_device' },
  '/asset/dept-inventory-apply': { title: '科室盘点申请', apiBase: '/asset', table: 'medical_device' },
  '/asset/dept-inventory-report': { title: '设备盘点报表', apiBase: '/asset', table: 'medical_device' },
  '/asset/import': {
    title: '资产导入',
    apiBase: '/asset',
    table: 'medical_device',
    importable: true,
    importUrl: '/asset/medical_device/import',
    importTemplateUrl: '/asset/medical_device/import/template'
  },
  '/asset/device': {
    title: '资产登记',
    apiBase: '/asset',
    table: 'medical_device',
    listPageUrl: '/asset/device/page',
    showRowIndex: true,
    rowIndexFixed: false,
    showRowSelection: true,
    keepFormOpenAfterSave: true,
    sortableColumns: ['device_code', 'device_name', 'specification', 'dept_name'],
    listParams: { hide_returned: true },
    listFilters: [
      { key: 'enable_dateFrom', label: '起', type: 'date', actionBar: true },
      { key: 'enable_dateTo', label: '止', type: 'date', actionBar: true },
      {
        key: 'device_status',
        label: '设备状态',
        dictType: 'device_status',
        multiple: true,
        actionBar: true,
        dictValues: ['normal', 'in_use', 'maintenance', 'scrap', 'pending_verify']
      }
    ],
    moreSearchFields: [
      { key: 'device_code', label: '资产编码', placeholder: '资产编码模糊' },
      { key: 'supplier_id', label: '供应商', placeholder: '供应商名称/编码', linkTable: 'supplier' },
      { key: 'manufacturer_id', label: '生产厂家', placeholder: '生产厂家名称/编码', linkTable: 'manufacturer' },
      { key: 'device_name', label: '资产名称', placeholder: '资产名称/简码' },
      { key: 'specification', label: '规格', placeholder: '规格模糊' },
      { key: 'model', label: '型号', placeholder: '型号模糊' },
      { key: 'dept_id', label: '科室', placeholder: '科室名称/编码', linkTable: 'department' },
      { key: 'manage_dept_id', label: '管理科室', placeholder: '科室名称/编码', linkTable: 'department' },
      { key: 'serial_number', label: '序列号(SN)', placeholder: '序列号模糊' }
    ],
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
    formDrawerSize: 'xl',
    formGroupColumns: { basic: 6 },
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'entry_no',
      'warehouse_name',
      'entry_date',
      'entry_type',
      'contract_code',
      'supplier_name',
      'created_by_name',
      'created_at',
      'total_amount',
      'approval_status',
      'approved_by_name',
      'approved_at'
    ],
    listFilters: [{ key: 'approval_status', label: '状态', dictType: 'acceptance_review_status' }]
  },
  '/asset/stock': {
    title: '库存查询',
    apiBase: '/asset',
    table: 'medical_device',
    listPageUrl: '/asset/device/page',
    showRowIndex: true,
    showRowSelection: true,
    sortableColumns: ['device_code', 'device_name', 'specification'],
    listParams: { stock_scope: 'warehouse' },
    columns: [
      'device_code',
      'device_name',
      'brand',
      'specification',
      'model',
      'warehouse_name',
      'stock_quantity',
      'original_value',
      'net_value',
      'enable_date',
      'device_status'
    ],
    listFilters: [
      { key: 'warehouse_id', label: '仓库', linkTable: 'warehouse', prepend: true },
      { key: 'enable_dateFrom', label: '起', type: 'date', actionBar: true },
      { key: 'enable_dateTo', label: '止', type: 'date', actionBar: true }
    ],
    moreSearchFields: [
      { key: 'supplier_id', label: '供应商', placeholder: '供应商名称/编码', linkTable: 'supplier' },
      { key: 'manufacturer_id', label: '生产厂家', placeholder: '生产厂家名称/编码', linkTable: 'manufacturer' },
      { key: 'device_name', label: '资产名称', placeholder: '资产名称/简码' },
      { key: 'specification', label: '规格', placeholder: '规格模糊' },
      { key: 'model', label: '型号', placeholder: '型号模糊' },
      { key: 'manage_dept_id', label: '管理科室', placeholder: '科室名称/编码', linkTable: 'department' },
      { key: 'serial_number', label: '序列号(SN)', placeholder: '序列号模糊' }
    ],
    enableView: true
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
    formDrawerSize: 'xl',
    formGroupColumns: { basic: 7 },
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'outbound_no',
      'warehouse_name',
      'dept_id',
      'created_by_name',
      'outbound_date',
      'receiver_id',
      'total_amount',
      'approved_by_name',
      'approved_at',
      'approval_status',
      'remark'
    ],
    listFilters: [{ key: 'approval_status', label: '审批状态', dictType: 'acceptance_review_status' }]
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
    formDrawerSize: 'xl',
    formGroupColumns: { basic: 6 },
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'entry_no',
      'warehouse_name',
      'entry_date',
      'entry_type',
      'contract_code',
      'supplier_name',
      'created_by_name',
      'created_at',
      'total_amount',
      'approval_status',
      'approved_by_name',
      'approved_at'
    ],
    listFilters: [{ key: 'approval_status', label: '状态', dictType: 'acceptance_review_status' }]
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
    formDrawerSize: 'xl',
    formGroupColumns: { basic: 7 },
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'outbound_no',
      'warehouse_name',
      'dept_id',
      'created_by_name',
      'outbound_date',
      'receiver_id',
      'total_amount',
      'approved_by_name',
      'approved_at',
      'approval_status',
      'remark'
    ],
    listFilters: [{ key: 'approval_status', label: '审批状态', dictType: 'acceptance_review_status' }]
  },
  '/warehouse/return': {
    title: '设备退库',
    apiBase: '/asset',
    table: 'device_return',
    masterDetail: true,
    detailTable: 'device_return_item',
    foreignKey: 'return_id',
    listPageUrl: '/asset/return/page',
    saveUrl: '/asset/return',
    formDrawerSize: 'xl',
    formGroupColumns: { basic: 7 },
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'return_no',
      'warehouse_name',
      'dept_id',
      'return_date',
      'created_by_name',
      'total_amount',
      'approved_by_name',
      'approved_at',
      'approval_status',
      'reason',
      'remark'
    ],
    listFilters: [{ key: 'approval_status', label: '审批状态', dictType: 'acceptance_review_status' }]
  },
  '/warehouse/goods-return': {
    title: '设备退货',
    apiBase: '/asset',
    table: 'device_goods_return',
    masterDetail: true,
    detailTable: 'device_goods_return_item',
    foreignKey: 'return_id',
    listPageUrl: '/asset/goods-return/page',
    saveUrl: '/asset/goods-return',
    formDrawerSize: 'xl',
    formGroupColumns: { basic: 7 },
    showRowIndex: true,
    showRowSelection: true,
    columns: [
      'return_no',
      'warehouse_name',
      'supplier_name',
      'created_by_name',
      'created_at',
      'total_amount',
      'approval_status',
      'approved_by_name',
      'approved_at',
      'reason'
    ],
    listFilters: [{ key: 'approval_status', label: '审批状态', dictType: 'acceptance_review_status' }]
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
  '/warehouse/scrap': { title: '报废申请', apiBase: '/asset', table: 'device_scrap', saveUrl: '/asset/scrap' },
  '/warehouse/scrap-review': { title: '报废审核', apiBase: '/asset', table: 'device_scrap', saveUrl: '/asset/scrap' },
  '/warehouse/scrap-query': { title: '报废查询', apiBase: '/asset', table: 'device_scrap', saveUrl: '/asset/scrap' },
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
  '/repair/engineer': { title: '维修工程师管理', apiBase: '/repair', table: 'sys_user' },
  '/repair/spare-archive': { title: '配件档案管理', apiBase: '/repair', table: 'spare_part', pinyinCode: true },
  '/repair/fault': { title: '故障库', apiBase: '/repair', table: 'fault_type_dict',
  enableView: true
},
  '/repair/process-type': { title: '维修进程类型', apiBase: '/repair', table: 'repair_process_type', enableView: true },
  '/maintain/param': { title: '保养参数设置', apiBase: '/maintain', table: 'maintenance_level' },
  '/maintain/plan': {
    title: '保养计划',
    apiBase: '/maintain',
    table: 'maintenance_plan',
    saveUrl: '/maintain/plan',
    showRowIndex: true,
    showRowSelection: true
  },
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
  '/maintain/device': {
    title: '保养设备管理',
    apiBase: '/maintain',
    table: 'ops_maintain_device',
    listPageUrl: '/maintain/device/page',
    hideAdd: true
  },
  '/inspect/param': { title: '巡检参数设置', apiBase: '/inspect', table: 'inspection_type' },
  '/inspect/plan': {
    title: '巡检计划',
    apiBase: '/inspect',
    table: 'inspection_plan',
    saveUrl: '/inspect/plan',
    showRowIndex: true,
    showRowSelection: true
  },
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
  '/inspect/device': {
    title: '巡检设备管理',
    apiBase: '/inspect',
    table: 'ops_inspect_device',
    listPageUrl: '/inspect/device/page',
    hideAdd: true
  },
  '/metrology/param': { title: '计量参数设置', apiBase: '/metrology', table: 'metrology_category' },
  '/metrology/plan': {
    title: '计量计划',
    apiBase: '/metrology',
    table: 'metrology_plan',
    saveUrl: '/metrology/plan',
    showRowIndex: true,
    showRowSelection: true
  },
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
  '/pm/plan': { title: '预防性维护计划', apiBase: '/pm', table: 'pm_plan', saveUrl: '/pm/plan', loadFormDetail: true },
  '/pm/execution': {
    title: '预防性维护执行',
    apiBase: '/pm',
    table: 'pm_execution',
    listPageUrl: '/pm/execution/page'
  },
  '/pm/query': { title: '预防性维护记录', apiBase: '/pm', table: 'pm_execution_item' },
  '/pm/device': {
    title: '预防性维护设备管理',
    apiBase: '/pm',
    table: 'ops_pm_device',
    listPageUrl: '/pm/device/page',
    hideAdd: true
  },
  '/analytics/mapping': {
    title: '效益分析对照',
    apiBase: '/analytics',
    table: 'benefit_mapping',
    listPageUrl: '/analytics/mapping/page',
    saveUrl: '/analytics/mapping'
  },
  '/analytics/efficiency': { title: '效率分析', apiBase: '/analytics', table: 'medical_device' },
  '/analytics/benefit-query': { title: '效益分析查询', apiBase: '/analytics', table: 'medical_device' },
  '/analytics/charge-audit': { title: '收费项目审核', apiBase: '/analytics', table: 'medical_device' },
  '/analytics/sync': { title: '效益分析提取', apiBase: '/analytics', table: 'integration_sync_task' },
  '/analytics/summary': {
    title: '效益分析报表',
    apiBase: '/analytics',
    table: 'device_benefit_summary',
    listPageUrl: '/analytics/summary/page',
    listFilters: [{ key: 'benefitLevel', label: '效益等级', dictType: 'benefit_level' }]
  },
  '/analytics/cost': {
    title: '效益分析上报',
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
  '/screen/warehouse-twin': { title: '数字孪生大屏', apiBase: '/screen', table: 'warehouse' },
  '/system/campus': { title: '院区管理', apiBase: '/system', table: 'campus',
  enableView: true
},
  '/system/warehouse': { title: '仓库维护', apiBase: '/system', table: 'warehouse',
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
  '/system/approval': { title: '审批配置', apiBase: '/system', table: 'sys_approval_flow' },
  '/system/supplier': {
    title: '供应商管理',
    apiBase: '/system',
    table: 'supplier',
    importable: true,
    pinyinCode: true,
    toolbarLayout: 'actions-row',
    formPlacement: 'right',
    showRowIndex: true,
    sortableColumns: ['supplier_code', 'supplier_name'],
    enableView: true
  },
  '/system/category': {
    title: '设备分类',
    apiBase: '/system',
    table: 'medical_device_category',
    formPlacement: 'right',
    showRowIndex: true,
    importable: true,
    sortableColumns: ['category_code', 'category_name', 'parent_code'],
    enableView: true
  },
  '/system/manufacturer': {
    title: '生产厂商',
    apiBase: '/system',
    table: 'manufacturer',
    importable: true,
    pinyinCode: true,
    toolbarLayout: 'actions-row',
    formPlacement: 'right',
    enableView: true
  },
  '/system/config': { title: '系统配置', apiBase: '/system', table: 'sys_config',
  enableView: true
}
}

export function getPageConfig(path: string): PageConfig | undefined {
  return pageRegistry[path]
}
