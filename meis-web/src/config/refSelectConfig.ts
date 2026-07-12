/** 外键选择器 API 配置 */
export interface RefSelectMeta {
  url: string
  labelKey: string
  codeKey?: string
  valueKey?: string
}

export const refSelectConfig: Record<string, RefSelectMeta> = {
  department: { url: '/system/departments', labelKey: 'dept_name', codeKey: 'dept_code' },
  campus: { url: '/system/campuses', labelKey: 'campus_name', codeKey: 'campus_code' },
  building: { url: '/system/building/list', labelKey: 'building_name', codeKey: 'building_code' },
  sys_user: { url: '/system/users', labelKey: 'real_name', codeKey: 'username' },
  supplier: { url: '/system/supplier/list', labelKey: 'supplier_name', codeKey: 'supplier_code' },
  manufacturer: { url: '/system/manufacturer/list', labelKey: 'manufacturer_name', codeKey: 'manufacturer_code' },
  medical_device_category: { url: '/system/medical_device_category/list', labelKey: 'category_name' },
  asset_category: { url: '/system/asset_category/list', labelKey: 'category_name' },
  finance_category: { url: '/system/finance_category/list', labelKey: 'finance_name' },
  unit_dict: { url: '/system/unit_dict/list', labelKey: 'unit_name' },
  medical_device: { url: '/asset/medical_device/list', labelKey: 'device_name', codeKey: 'device_code' },
  device_outbound: { url: '/asset/device_outbound/list', labelKey: 'outbound_no', codeKey: 'outbound_no' },
  inspection_plan: { url: '/inspect/inspection_plan/list', labelKey: 'plan_name', codeKey: 'plan_code' },
  maintenance_plan: { url: '/maintain/maintenance_plan/list', labelKey: 'plan_name', codeKey: 'plan_code' },
  emergency_device_pool: { url: '/special/emergency_device_pool/list', labelKey: 'pool_name' },
  shared_device: { url: '/shared/device/list', labelKey: 'device_name', codeKey: 'device_code' },
  shared_medical_device: { url: '/shared/device/list', labelKey: 'device_name', codeKey: 'device_code' },
  shared_device_candidate: { url: '/shared/device/candidates/list', labelKey: 'device_name', codeKey: 'device_code' },
  shared_device_loan: { url: '/shared/loan/list', labelKey: 'loan_no', codeKey: 'loan_no' },
  pm_type: { url: '/maintain/pm_type/list', labelKey: 'type_name', codeKey: 'type_code' },
  pm_template: { url: '/maintain/pm_template/list', labelKey: 'template_name', codeKey: 'template_code' },
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
  metrology_type: { url: '/metrology/type/list', labelKey: 'type_name', codeKey: 'type_code' },
  metrology_type_code: { url: '/metrology/type/list', labelKey: 'type_name', valueKey: 'type_code' },
  metrology_org: { url: '/metrology/metrology_org/list', labelKey: 'org_name' },
  metrology_template: { url: '/metrology/metrology_template/list', labelKey: 'template_name' },
  power_base_station: { url: '/power/power_base_station/list', labelKey: 'station_name' }
}
