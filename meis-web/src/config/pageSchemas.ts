import { businessSchemas } from './businessSchemas'

export type FieldGroup =
  | 'basic'
  | 'finance'
  | 'location'
  | 'approval'
  | 'attachment'
  | 'remark'
  | 'detail'
  | 'workflow'
  | 'time'
  | 'status'
  | 'other'

export interface FieldSchema {
  prop: string
  label: string
  type?: 'text' | 'number' | 'date' | 'datetime' | 'select' | 'textarea' | 'boolean' | 'json'
  dictType?: string
  linkTable?: string
  group?: FieldGroup
  required?: boolean
  readonly?: boolean
  list?: boolean
  detail?: boolean
  width?: number
  span?: number
}

export const tableSchemas: Record<string, FieldSchema[]> = {
  ...businessSchemas,
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

export function getListFields(table: string): FieldSchema[] {
  const schema = getSchema(table)
  const listed = schema.filter((f) => f.list)
  return listed.length ? listed : schema.slice(0, 12)
}

export function getDetailFields(table: string): FieldSchema[] {
  const schema = getSchema(table)
  const detail = schema.filter((f) => f.detail)
  return detail.length ? detail : schema.filter((f) => !f.readonly).slice(0, 10)
}

export function getGroupedFields(table: string): { group: FieldGroup; fields: FieldSchema[] }[] {
  const schema = getSchema(table)
  const groups = new Map<FieldGroup, FieldSchema[]>()
  for (const f of schema) {
    const g = f.group ?? 'other'
    if (!groups.has(g)) groups.set(g, [])
    groups.get(g)!.push(f)
  }
  const order: FieldGroup[] = ['basic', 'finance', 'location', 'time', 'status', 'workflow', 'approval', 'attachment', 'remark', 'other']
  return order.filter((g) => groups.has(g)).map((g) => ({ group: g, fields: groups.get(g)! }))
}

const groupTitleMap: Record<FieldGroup, string> = {
  basic: '基本信息',
  finance: '财务信息',
  location: '位置信息',
  time: '时间信息',
  status: '状态信息',
  workflow: '流程信息',
  approval: '审批信息',
  attachment: '附件',
  remark: '备注',
  detail: '明细',
  other: '其他'
}

export function groupTitle(g: FieldGroup) {
  return groupTitleMap[g] ?? g
}
