/** 外键选择器 API 配置 */
export interface RefSelectMeta {
  url: string
  labelKey: string
  valueKey?: string
}

export const refSelectConfig: Record<string, RefSelectMeta> = {
  department: { url: '/system/departments', labelKey: 'dept_name' },
  campus: { url: '/system/campuses', labelKey: 'campus_name' },
  building: { url: '/system/building/list', labelKey: 'building_name' },
  sys_user: { url: '/system/users', labelKey: 'real_name' },
  supplier: { url: '/system/supplier/list', labelKey: 'supplier_name' },
  manufacturer: { url: '/system/manufacturer/list', labelKey: 'manufacturer_name' },
  medical_device_category: { url: '/system/medical_device_category/list', labelKey: 'category_name' },
  asset_category: { url: '/system/asset_category/list', labelKey: 'category_name' },
  finance_category: { url: '/system/finance_category/list', labelKey: 'finance_name' },
  unit_dict: { url: '/system/unit_dict/list', labelKey: 'unit_name' },
  medical_device: { url: '/asset/medical_device/list', labelKey: 'device_name' },
  purchase_plan: { url: '/purchase/purchase_plan/list', labelKey: 'plan_code' },
  purchase_project: { url: '/purchase/purchase_project/list', labelKey: 'project_name' },
  purchase_contract: { url: '/purchase/purchase_contract/list', labelKey: 'contract_code' },
  engineer: { url: '/repair/engineer/list', labelKey: 'real_name' },
  fault_type_dict: { url: '/repair/fault_type_dict/list', labelKey: 'fault_name' },
  warehouse: { url: '/system/warehouses', labelKey: 'warehouse_name' },
  maintenance_template: { url: '/maintain/maintenance_template/list', labelKey: 'template_name' },
  maintenance_level: { url: '/maintain/maintenance_level/list', labelKey: 'level_name' },
  inspection_template: { url: '/inspect/inspection_template/list', labelKey: 'template_name' },
  inspection_type: { url: '/inspect/inspection_type/list', labelKey: 'type_name' },
  metrology_category: { url: '/metrology/metrology_category/list', labelKey: 'category_name' },
  metrology_org: { url: '/metrology/metrology_org/list', labelKey: 'org_name' },
  metrology_template: { url: '/metrology/metrology_template/list', labelKey: 'template_name' }
}
