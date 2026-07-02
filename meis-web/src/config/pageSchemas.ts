export interface FieldSchema {
  prop: string
  label: string
  type?: 'text' | 'number' | 'date' | 'select' | 'textarea'
  dictType?: string
  required?: boolean
  readonly?: boolean
  list?: boolean
}

export const tableSchemas: Record<string, FieldSchema[]> = {
  purchase_plan: [
    { prop: 'plan_code', label: '计划编号', list: true },
    { prop: 'plan_year', label: '年度', type: 'number', list: true },
    { prop: 'total_budget', label: '预算总额', type: 'number', list: true },
    { prop: 'approval_status', label: '审批状态', dictType: 'approval_status', list: true, readonly: true },
    { prop: 'justification', label: '论证说明', type: 'textarea' }
  ],
  purchase_project: [
    { prop: 'project_code', label: '项目编号', list: true },
    { prop: 'project_name', label: '项目名称', list: true },
    { prop: 'purchase_method', label: '采购方式', dictType: 'purchase_method', list: true },
    { prop: 'status', label: '状态', list: true }
  ],
  purchase_contract: [
    { prop: 'contract_code', label: '合同编号', list: true },
    { prop: 'contract_name', label: '合同名称', list: true },
    { prop: 'contract_amount', label: '合同金额', type: 'number', list: true },
    { prop: 'approval_status', label: '审批状态', list: true }
  ],
  medical_device: [
    { prop: 'device_code', label: '设备编码', list: true },
    { prop: 'device_name', label: '设备名称', list: true },
    { prop: 'brand', label: '品牌', list: true },
    { prop: 'device_status', label: '状态', dictType: 'device_status', list: true }
  ],
  repair_workorder: [
    { prop: 'wo_no', label: '工单号', list: true, readonly: true },
    { prop: 'device_name', label: '设备', list: true },
    { prop: 'fault_description', label: '故障描述', type: 'textarea', list: true },
    { prop: 'urgency_level', label: '紧急度', dictType: 'urgency', list: true },
    { prop: 'status', label: '状态', list: true, readonly: true }
  ],
  sys_user: [
    { prop: 'username', label: '用户名', list: true },
    { prop: 'real_name', label: '姓名', list: true },
    { prop: 'is_active', label: '启用', list: true }
  ],
  sys_role: [
    { prop: 'role_code', label: '角色编码', list: true },
    { prop: 'role_name', label: '角色名称', list: true },
    { prop: 'description', label: '描述', list: true }
  ],
  campus: [
    { prop: 'campus_code', label: '院区编码', list: true, required: true },
    { prop: 'campus_name', label: '院区名称', list: true, required: true },
    { prop: 'address', label: '地址', list: true },
    { prop: 'contact_phone', label: '联系电话', list: true },
    { prop: 'is_active', label: '启用', list: true }
  ],
  department: [
    { prop: 'dept_code', label: '科室编码', list: true, required: true },
    { prop: 'dept_name', label: '科室名称', list: true, required: true },
    { prop: 'is_clinical', label: '临床科室', list: true },
    { prop: 'sort_order', label: '排序', type: 'number', list: true },
    { prop: 'is_active', label: '启用', list: true }
  ],
  warehouse: [
    { prop: 'warehouse_code', label: '库房编码', list: true, required: true },
    { prop: 'warehouse_name', label: '库房名称', list: true, required: true },
    { prop: 'address', label: '地址', list: true },
    { prop: 'is_active', label: '启用', list: true }
  ],
  sys_dict: [
    { prop: 'dict_type', label: '字典类型', list: true, required: true },
    { prop: 'dict_code', label: '编码', list: true, required: true },
    { prop: 'dict_label', label: '标签', list: true, required: true },
    { prop: 'dict_value', label: '值', list: true },
    { prop: 'sort_order', label: '排序', type: 'number', list: true }
  ],
  sys_operation_log: [
    { prop: 'created_at', label: '时间', list: true },
    { prop: 'module_name', label: '模块', list: true },
    { prop: 'operation_desc', label: '操作', list: true },
    { prop: 'ip_address', label: 'IP', list: true }
  ]
}

export function getSchema(table: string): FieldSchema[] {
  return tableSchemas[table] ?? []
}
