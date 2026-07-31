import type { FieldSchema } from '@/config/pageSchemas'

/**
 * PLT-DEV-LIST-01：从属明细表可展示的台账字段（只读冗余/联查）。
 * 不含资产编码/名称（各表明细已自有）；调用方插在编码名称之后。
 */
export const deviceLedgerDetailFields: FieldSchema[] = [
  { prop: 'brand', label: '品牌', detail: true, readonly: true, width: 90 },
  { prop: 'model', label: '型号', detail: true, readonly: true, width: 100 },
  { prop: 'registration_no', label: '医疗器械注册证号', detail: true, readonly: true, width: 140 },
  { prop: 'production_date', label: '生产日期', type: 'date', detail: true, readonly: true, width: 110 },
  { prop: 'acceptance_date', label: '验收日期', type: 'date', detail: true, readonly: true, width: 110 },
  { prop: 'enable_date', label: '启用日期', type: 'date', detail: true, readonly: true, width: 110 },
  { prop: 'serial_number', label: '序列号(SN)', detail: true, readonly: true, width: 120 },
  { prop: 'has_power_tag', label: '是否有电流标签', type: 'boolean', detail: true, readonly: true, width: 120 },
  { prop: 'power_tag_code', label: '电流监测编码', detail: true, readonly: true, width: 120 },
  { prop: 'dept_name', label: '科室', detail: true, readonly: true, width: 110 },
  { prop: 'warehouse_name', label: '仓库', detail: true, readonly: true, width: 110 },
  { prop: 'install_location', label: '安装位置', detail: true, readonly: true, width: 140 },
  { prop: 'location_detail', label: '存放位置', detail: true, readonly: true, width: 140 },
  { prop: 'responsible_person_name', label: '责任人', detail: true, readonly: true, width: 100 },
  { prop: 'manufacturer_code', label: '厂家编码', detail: true, readonly: true, width: 110 },
  { prop: 'manufacturer_name', label: '厂家名称', detail: true, readonly: true, width: 130 },
  { prop: 'supplier_code', label: '供应商编码', detail: true, readonly: true, width: 110 },
  { prop: 'supplier_name', label: '供应商名称', detail: true, readonly: true, width: 130 },
  { prop: 'device_status', label: '设备状态', dictType: 'device_status', detail: true, readonly: true, width: 100 }
]
