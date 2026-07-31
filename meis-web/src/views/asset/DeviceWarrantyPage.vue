<template>
  <div class="device-warranty-page">
    <CrudPage
      :config="config"
      delete-url="/asset/warranty"
      enable-view
    >
      <template #form="{ form, fields, mode }">
        <el-form label-width="110px" :disabled="mode === 'view'">
          <GroupedFormFields table="device_warranty" :model="form" :fields="fields">
            <template #field-supplier_id="{ model }">
              <div class="supplier-row">
                <RefSelect
                  :model-value="model.supplier_id"
                  link-table="supplier"
                  placeholder="选择维保公司；清空=院内自主"
                  :disabled="mode === 'view'"
                  @update:model-value="(v) => onSupplier(model, v)"
                />
              </div>
            </template>
          </GroupedFormFields>
        </el-form>

        <FormSection title="覆盖设备" class="devices-section">
          <div class="devices-toolbar">
            <el-button v-if="mode !== 'view'" type="primary" size="small" @click="openPicker(form)">
              添加设备
            </el-button>
            <span class="amount-hint">
              单价合计 {{ formatMoney(sumUnitPrice(form)) }}
              <template v-if="form.total_amount != null && form.total_amount !== ''">
                / 总价 {{ formatMoney(Number(form.total_amount)) }}
              </template>
            </span>
          </div>
          <el-table :data="ensureDevices(form)" border size="small" class="system-table">
            <el-table-column prop="device_code" label="资产编码" width="140" show-overflow-tooltip />
            <el-table-column prop="device_name" label="资产名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="单价" width="140">
              <template #default="{ row }">
                <el-input-number
                  v-if="mode !== 'view'"
                  v-model="row.unit_price"
                  :controls="true"
                  :min="0"
                  style="width: 100%"
                />
                <span v-else>{{ formatMoney(row.unit_price) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row, $index }">
                <el-button
                  v-if="row.id"
                  link
                  type="primary"
                  @click="openLinkChangeLog(row)"
                >
                  变更记录
                </el-button>
                <el-button
                  v-if="mode !== 'view'"
                  link
                  type="danger"
                  @click="removeDevice(form, $index)"
                >
                  移除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <PageEmpty description="暂无覆盖设备" :image-size="64" />
            </template>
          </el-table>
        </FormSection>
      </template>
    </CrudPage>

    <AssetDevicePicker v-model="pickerVisible" @confirm="onPicked" />
    <EntityChangeHistoryDrawer
      v-model="linkLogVisible"
      entity-type="device_warranty_device"
      :entity-id="linkLogId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import AssetDevicePicker from '@/components/form/AssetDevicePicker.vue'
import PageEmpty from '@/components/table/PageEmpty.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/asset/warranty-term')!

const pickerVisible = ref(false)
const pickerForm = ref<Record<string, unknown> | null>(null)
const linkLogVisible = ref(false)
const linkLogId = ref('')

function onSupplier(model: Record<string, unknown>, value: unknown) {
  model.supplier_id = value != null && String(value) ? String(value) : null
  if (!model.supplier_id) {
    model.supplier_name = '院内自主'
  }
}

function ensureDevices(form: Record<string, unknown>) {
  if (!Array.isArray(form.devices)) form.devices = []
  return form.devices as Record<string, unknown>[]
}

function openPicker(form: Record<string, unknown>) {
  pickerForm.value = form
  pickerVisible.value = true
}

function onPicked(device: Record<string, unknown>) {
  const form = pickerForm.value
  if (!form) return
  const devices = ensureDevices(form)
  const id = String(device.id ?? '')
  if (!id) return
  if (devices.some((d) => String(d.device_id) === id)) {
    ElMessage.warning('该设备已在覆盖列表中')
    return
  }
  devices.push({
    device_id: id,
    device_code: device.device_code ?? null,
    device_name: device.device_name ?? null,
    unit_price: undefined
  })
  pickerVisible.value = false
}

function removeDevice(form: Record<string, unknown>, index: number) {
  ensureDevices(form).splice(index, 1)
}

function sumUnitPrice(form: Record<string, unknown>) {
  return ensureDevices(form).reduce((acc, row) => {
    const n = row.unit_price != null && row.unit_price !== '' ? Number(row.unit_price) : 0
    return acc + (Number.isFinite(n) ? n : 0)
  }, 0)
}

function formatMoney(v: unknown) {
  if (v == null || v === '') return '—'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : '—'
}

function openLinkChangeLog(row: Record<string, unknown>) {
  linkLogId.value = String(row.id ?? '')
  linkLogVisible.value = true
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
.devices-section {
  margin-top: 16px;
}
.devices-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.amount-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
