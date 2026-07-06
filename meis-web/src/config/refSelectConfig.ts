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
  medical_device: { url: '/asset/medical_device/list', labelKey: 'device_name' },
  purchase_plan: { url: '/purchase/purchase_plan/list', labelKey: 'plan_code' },
  purchase_project: { url: '/purchase/purchase_project/list', labelKey: 'project_name' },
  purchase_contract: { url: '/purchase/purchase_contract/list', labelKey: 'contract_code' },
  engineer: { url: '/repair/engineer/list', labelKey: 'real_name' },
  warehouse: { url: '/system/warehouse/list', labelKey: 'warehouse_name' },
  maintenance_template: { url: '/maintain/maintenance_template/list', labelKey: 'template_name' }
}
