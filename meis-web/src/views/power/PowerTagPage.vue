<template>
  <div class="power-tag-page">
    <CrudPage ref="crudRef" :config="pageConfig" detail-mode @add="openCreate" @detail="openEdit">
      <template #row-actions="{ row }">
        <el-button
          link
          type="primary"
          :disabled="!row.device_id"
          @click="openStandbyLimits(row)"
        >
          待机电流
        </el-button>
        <el-button link type="primary" @click="openReadings(row)">监测记录</el-button>
        <el-button link type="primary" @click="openBindLog(row)">绑定记录</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="formVisible" :title="formTitle" size="lg">
      <template v-if="record">
        <el-form label-width="140px">
          <GroupedFormFields :table="pageConfig.table" :model="record" :fields="formFields">
            <template #field-device_id="{ model }">
              <AssetDevicePickerField
                :model-value="model"
                @update:model-value="(v) => applyDevicePicker(model, v)"
              />
            </template>
            <template #field-station_id="{ model }">
              <PowerStationPickerField
                :model-value="model"
                @update:model-value="(v) => applyStationPicker(model, v)"
              />
            </template>
          </GroupedFormFields>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>

    <PowerTagStandbyLimitsDialog
      v-model="standbyVisible"
      :tag-id="activeTagId"
      @saved="crudRef?.load()"
    />
    <PowerCurrentReadingDialog
      v-model="readingVisible"
      :title="readingTitle"
      :list-url="readingListUrl"
      :export-url="readingExportUrl"
    />
    <PowerTagBindLogDialog v-model="bindLogVisible" :tag-id="activeTagId" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import AssetDevicePickerField from '@/components/form/AssetDevicePickerField.vue'
import PowerStationPickerField from '@/components/form/PowerStationPickerField.vue'
import PowerCurrentReadingDialog from '@/components/power/PowerCurrentReadingDialog.vue'
import PowerTagBindLogDialog from '@/components/power/PowerTagBindLogDialog.vue'
import PowerTagStandbyLimitsDialog from '@/components/power/PowerTagStandbyLimitsDialog.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const baseConfig = getPageConfig('/power/tag')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const pageConfig = computed(() => ({ ...baseConfig, saveUrl: '/power/tag' }))

const formVisible = ref(false)
const record = ref<Record<string, unknown> | null>(null)
const standbyVisible = ref(false)
const readingVisible = ref(false)
const readingTitle = ref('监测记录')
const readingListUrl = ref('')
const readingExportUrl = ref<string>()
const bindLogVisible = ref(false)
const activeTagId = ref<string>()

const formTitle = computed(() => (record.value?.id ? '编辑标签' : '新增标签'))
const formFields = computed(() => getSchema('power_tag').filter((f) => !f.readonly))

function openCreate() {
  record.value = { is_active: true }
  formVisible.value = true
}

async function openEdit(row: Record<string, unknown>) {
  const { data } = await http.get(`/power/tag/${row.id}`)
  record.value = data.data ?? { ...row }
  formVisible.value = true
}

function openStandbyLimits(row: Record<string, unknown>) {
  activeTagId.value = String(row.id)
  standbyVisible.value = true
}

function openReadings(row: Record<string, unknown>) {
  const id = String(row.id)
  readingTitle.value = `监测记录 - ${row.tag_code ?? ''}`
  readingListUrl.value = `/power/tag/${id}/readings/page`
  readingExportUrl.value = `/power/tag/${id}/readings/export`
  readingVisible.value = true
}

function openBindLog(row: Record<string, unknown>) {
  activeTagId.value = String(row.id)
  bindLogVisible.value = true
}

function applyDevicePicker(model: Record<string, unknown>, value: Record<string, unknown>) {
  const nextId = value.device_id ?? null
  const prevId = model.device_id ?? null
  model.device_id = nextId
  model.device_code = value.device_code ?? null
  model.device_name = value.device_name ?? null
  if (String(nextId ?? '') !== String(prevId ?? '')) {
    model.specification = value.specification ?? null
    model.model = value.model ?? null
    model.serial_number = value.serial_number ?? null
    model.manufacturer_name = value.manufacturer_name ?? null
    model.dept_name = value.dept_name ?? null
  }
}

function applyStationPicker(model: Record<string, unknown>, value: Record<string, unknown>) {
  model.station_id = value.station_id ?? null
  model.station_code = value.station_code ?? null
  model.station_name = value.station_name ?? null
}

function buildSavePayload(rec: Record<string, unknown>) {
  return {
    id: rec.id,
    tag_code: rec.tag_code,
    tag_name: rec.tag_name,
    device_id: rec.device_id ?? null,
    station_id: rec.station_id ?? null,
    rated_power: rec.rated_power,
    install_date: rec.install_date,
    is_active: rec.is_active ?? true,
    remark: rec.remark
  }
}

async function save() {
  if (!record.value) return
  const { data } = await http.post('/power/tag', buildSavePayload(record.value))
  record.value = data.data
  formVisible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}

defineExpose({ openCreate })
</script>
