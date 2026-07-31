<template>
  <div class="device-warranty-term-page">
    <CrudPage :config="config" delete-url="/asset/warranty-term">
      <template #form="{ form, fields, mode }">
        <el-form label-width="110px">
          <GroupedFormFields table="device_warranty_term" :model="form" :fields="fields">
            <template #field-device_id="{ model }">
              <AssetDevicePickerField
                :model-value="model"
                :disabled="mode === 'view'"
                @update:model-value="(v) => applyDevice(model, v)"
              />
            </template>
            <template #field-supplier_id="{ model }">
              <div class="supplier-row">
                <RefSelect
                  :model-value="model.supplier_id"
                  link-table="supplier"
                  placeholder="选择维保公司；清空=院内自主"
                  :disabled="mode === 'view'"
                  @update:model-value="(v) => onSupplier(model, v)"
                />
                <el-button
                  v-if="mode !== 'view'"
                  link
                  type="primary"
                  :disabled="!model.device_id"
                  @click="fillSupplierFromManufacturer(model)"
                >
                  带出设备生产厂家
                </el-button>
              </div>
            </template>
          </GroupedFormFields>
        </el-form>
      </template>
    </CrudPage>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import AssetDevicePickerField from '@/components/form/AssetDevicePickerField.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/asset/warranty-term')!

function applyDevice(model: Record<string, unknown>, value: Record<string, unknown>) {
  Object.assign(model, {
    device_id: value.device_id != null ? String(value.device_id) : null,
    device_code: value.device_code ?? null,
    device_name: value.device_name ?? null,
    manufacturer_name: value.manufacturer_name ?? null
  })
}

function onSupplier(model: Record<string, unknown>, value: unknown) {
  model.supplier_id = value != null && String(value) ? String(value) : null
  if (!model.supplier_id) {
    model.supplier_name = '院内自主'
  }
}

async function fillSupplierFromManufacturer(model: Record<string, unknown>) {
  const name = String(model.manufacturer_name ?? '').trim()
  if (!name) {
    // 从设备详情再取一次
    if (!model.device_id) {
      ElMessage.warning('请先选择设备')
      return
    }
    try {
      const { data } = await http.get(`/asset/medical_device/${model.device_id}`)
      const mfr = String(data.data?.manufacturer_name ?? '').trim()
      if (!mfr) {
        ElMessage.warning('该设备未维护生产厂家')
        return
      }
      await matchSupplier(model, mfr)
    } catch {
      ElMessage.error('加载设备信息失败')
    }
    return
  }
  await matchSupplier(model, name)
}

async function matchSupplier(model: Record<string, unknown>, keyword: string) {
  const { data } = await http.get('/system/supplier/page', {
    params: { page: 1, size: 10, keyword }
  })
  const rows = (data.data?.records ?? []) as Record<string, unknown>[]
  if (!rows.length) {
    ElMessage.warning(`未在供应商中找到「${keyword}」，请先在供应商主数据建档`)
    return
  }
  const hit = rows[0]
  model.supplier_id = String(hit.id)
  model.supplier_name = hit.supplier_name ?? keyword
  ElMessage.success(`已带出供应商：${model.supplier_name}`)
}
</script>

<style scoped>
.supplier-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.supplier-row :deep(.filter-ref),
.supplier-row :deep(.el-select) {
  flex: 1;
  min-width: 0;
}
</style>
