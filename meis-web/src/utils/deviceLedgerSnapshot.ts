/** 从台账行提取 PLT-DEV-LIST-01 展示字段（写入从属明细前端态 / 冗余） */
export function deviceLedgerSnapshot(device: Record<string, unknown>) {
  return {
    device_id: device.id != null ? String(device.id) : device.device_id,
    device_code: device.device_code ?? null,
    device_name: device.device_name ?? null,
    brand: device.brand ?? null,
    specification: device.specification ?? null,
    model: device.model ?? null,
    registration_no: device.registration_no ?? null,
    production_date: device.production_date ?? null,
    serial_number: device.serial_number ?? null,
    has_power_tag: device.has_power_tag ?? false,
    power_tag_code: device.power_tag_code ?? null,
    dept_id: device.dept_id ?? null,
    dept_name: device.dept_name ?? null,
    warehouse_name: device.warehouse_name ?? null,
    manufacturer_code: device.manufacturer_code ?? null,
    manufacturer_name: device.manufacturer_name ?? null,
    supplier_code: device.supplier_code ?? null,
    supplier_name: device.supplier_name ?? null,
    device_status: device.device_status ?? null
  }
}
