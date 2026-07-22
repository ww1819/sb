<template>
  <el-form label-width="96px" class="device-ledger-form" :class="{ 'device-ledger-form--view': isView }">
    <FormTabNav v-model="activeTab" :tabs="visibleTabs" />

    <div class="device-ledger-form__panel">
      <GroupedFormFields
        v-show="activeTab === 'basic'"
        table="medical_device"
        :model="model"
        :fields="basicFields"
        :group-columns="{ basic: 5, finance: 5, location: 5, vendor: 5, time: 5, accounting: 5, status: 5, compliance: 5, other: 5 }"
        :group-rows="{ basic: basicFormRows, vendor: vendorFormRows, accounting: accountingFormRows, location: locationFormRows }"
        :group-panels="{ basic: basicFormPanel, status: statusFormPanel }"
        :highlight-labels="highlightLabels"
        :group-titles="{ finance: '折旧信息', time: '合同信息', accounting: '财务信息', status: '设备属性', compliance: '动态监测' }"
      />

      <div v-show="activeTab === 'card'" class="device-ledger-form__card-pane">
        <DeviceAssetCard :model="model" />
      </div>

      <DeviceArchivePanel v-show="activeTab === 'archive'" :readonly="isView" />
      <DeviceImagePanel v-show="activeTab === 'images'" :readonly="isView" />

      <DeviceRecordTablePanel
        v-show="activeTab === 'repair'"
        :columns="repairColumns"
        empty-text="暂无维修记录"
        filter-placeholder="工单号 / 故障描述"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'maintain'"
        :columns="maintainColumns"
        empty-text="暂无保养记录"
        filter-placeholder="记录号 / 计划名称"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inspection'"
        :columns="inspectionColumns"
        empty-text="暂无巡检记录"
        filter-placeholder="巡检单号"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'metrology'"
        :columns="metrologyColumns"
        empty-text="暂无计量记录"
        filter-placeholder="计量编号"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inventory'"
        :columns="inventoryColumns"
        empty-text="暂无盘点记录"
        filter-placeholder="盘点单号"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'shared_loan'"
        :columns="sharedLoanColumns"
        empty-text="暂无借调记录"
        filter-placeholder="借调单号"
        load-url="/shared/loan/page"
        :device-id="String(model.id ?? '')"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'shared_fee'"
        :columns="sharedFeeColumns"
        empty-text="暂无借调费用"
        filter-placeholder="收费单号"
        load-url="/shared/fee/page"
        :device-id="String(model.id ?? '')"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'adverse'"
        :columns="adverseColumns"
        empty-text="暂无不良事件"
        filter-placeholder="事件编号"
        load-url="/qc/adverse/page"
        :device-id="String(model.id ?? '')"
      />
      <DeviceCurrentReadingPanel
        v-show="activeTab === 'current'"
        :device-id="String(model.id ?? '')"
      />
      <DeviceLabelPanel
        v-show="activeTab === 'label'"
        :device-id="String(model.id ?? '')"
        :device-code="String(model.device_code ?? '')"
        :device-name="String(model.device_name ?? '')"
      />
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onUnmounted, ref, watch } from 'vue'
import http from '@/api/http'
import FormTabNav from '@/components/form/FormTabNav.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import DeviceAssetCard from '@/components/asset/DeviceAssetCard.vue'
import DeviceArchivePanel from '@/components/asset/tabs/DeviceArchivePanel.vue'
import DeviceImagePanel from '@/components/asset/tabs/DeviceImagePanel.vue'
import DeviceRecordTablePanel from '@/components/asset/tabs/DeviceRecordTablePanel.vue'
import DeviceCurrentReadingPanel from '@/components/asset/tabs/DeviceCurrentReadingPanel.vue'
import DeviceLabelPanel from '@/components/asset/tabs/DeviceLabelPanel.vue'
import type { RecordColumn } from '@/components/asset/tabs/DeviceRecordTablePanel.vue'
import { getSchema, type FieldSchema } from '@/config/pageSchemas'
import { toPinyinShortCode } from '@/utils/pinyinCode'

const props = withDefaults(
  defineProps<{
    model: Record<string, unknown>
    fields?: FieldSchema[]
    /** create | edit | view */
    mode?: 'create' | 'edit' | 'view'
  }>(),
  { mode: 'edit' }
)

const activeTab = ref('basic')
const isView = computed(() => props.mode === 'view')
const isCreate = computed(() => props.mode === 'create' || !props.model.id)

type CrudBeforeSaveApi = {
  register: (fn: () => void | Promise<void>) => void
  unregister: (fn: () => void | Promise<void>) => void
}
const crudBeforeSave = inject<CrudBeforeSaveApi | null>('crudBeforeSave', null)

const allTabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'card', label: '资产卡片' },
  { key: 'archive', label: '设备档案' },
  { key: 'images', label: '设备图片' },
  { key: 'label', label: '资产标签' },
  { key: 'repair', label: '维修记录' },
  { key: 'maintain', label: '保养记录' },
  { key: 'inspection', label: '巡检记录' },
  { key: 'metrology', label: '计量记录' },
  { key: 'shared_loan', label: '借调记录' },
  { key: 'shared_fee', label: '借调费用' },
  { key: 'inventory', label: '盘点记录' },
  { key: 'adverse', label: '不良事件' },
  { key: 'current', label: '电流度数' }
]

/** 新增：仅基本信息/档案/图片；编辑与查看：全部 Tab */
const visibleTabs = computed(() => {
  if (isCreate.value) {
    const createKeys = new Set(['basic', 'archive', 'images'])
    return allTabs.filter((t) => createKeys.has(t.key))
  }
  return allTabs
})

watch(
  () => props.mode,
  () => {
    activeTab.value = 'basic'
  }
)

const basicGroupKeys = new Set(['basic', 'finance', 'location', 'vendor', 'time', 'accounting', 'status', 'compliance', 'other', 'attachment'])

const basicFormRows = [
  ['device_code', 'card_code', 'device_name', 'pinyin_code', 'brand'],
  ['specification', 'model', 'serial_number', 'unit_id', 'category_id'],
  ['asset_category_id', 'finance_category_id', 'standby_current_max_ma', 'standby_current_min_ma'],
  ['country_of_origin', 'use_dept_head', 'dept_id', 'manage_dept_head', 'manage_dept_id'],
  ['registration_no', 'production_date']
]

const basicFormPanel = {
  inner: ['is_imported']
}

const highlightLabels = ['device_code', 'device_name', 'unit_id', 'dept_id']

const vendorFormRows = [
  ['supplier_uscc', 'supplier_id', 'supplier_contact', 'supplier_phone'],
  ['maintenance_uscc', 'maintenance_company', 'maintenance_engineer', 'maintenance_phone'],
  ['manufacturer_uscc', 'manufacturer_id']
]

const accountingFormRows = [
  ['material_category_code', 'material_group', 'asset_class_code', 'asset_class_name', 'acceptance_date'],
  ['kingdee_asset_code', 'invoice_no', 'invoice_date', 'expense_item_code', 'expense_item_name'],
  ['fund_source', 'lease_fee_per_use', 'lease_fee_per_day']
]

const locationFormRows = [
  ['campus_id', 'building_id', 'warehouse_id', 'location_floor', 'room_number'],
  ['location_detail']
]

const statusFormPanel = {
  outer: ['device_status', 'risk_level'],
  inner: [
    'is_life_support',
    'is_emergency',
    'is_metrology',
    'is_shared_device',
    'is_maintain_device',
    'is_inspection_device',
    'is_pm_device'
  ]
}

watch(
  () => props.model.device_name,
  (name, oldName) => {
    if (isView.value) return
    const next = toPinyinShortCode(String(name ?? ''))
    if (!next) return
    const current = String(props.model.pinyin_code ?? '').trim()
    // 初次加载或名称未变：仅当简码为空时按设备名称回填
    if (oldName === undefined || oldName === name) {
      if (!current) props.model.pinyin_code = next
      return
    }
    // 用户修改设备名称时同步更新简码
    props.model.pinyin_code = next
  },
  { immediate: true }
)

/** 用户是否手工改过分类；为 true 后名称变更/保存前都不再自动识别（AST-UI-11） */
const categoryTouchedByUser = ref(false)
let applyingAutoCategory = false
let categoryMatchTimer: ReturnType<typeof setTimeout> | null = null
let categoryMatchSeq = 0

function canAutoFillCategory() {
  return !categoryTouchedByUser.value
}

async function matchCategoryByDeviceName(deviceName: string) {
  if (isView.value) return
  if (!canAutoFillCategory()) return
  const name = deviceName.trim()
  if (!name) return
  const seq = ++categoryMatchSeq
  try {
    const { data } = await http.get('/system/medical_device_category/match-by-device-name', {
      params: { name }
    })
    if (seq !== categoryMatchSeq) return
    if (!canAutoFillCategory()) return
    const row = data?.data as
      | { id?: string; label?: string; category_code?: string; category_name?: string }
      | undefined
    if (!row?.id) return
    const id = String(row.id)
    const label =
      (row.label && String(row.label).trim()) ||
      `${row.category_code ?? ''} ${row.category_name ?? ''}`.trim()
    applyingAutoCategory = true
    props.model.category_id = id
    if (label) props.model.category_name = label
    await nextTick()
    applyingAutoCategory = false
  } catch {
    applyingAutoCategory = false
    /* 匹配失败不阻断表单编辑 */
  }
}

function scheduleCategoryMatch(deviceName: string) {
  if (categoryMatchTimer != null) clearTimeout(categoryMatchTimer)
  categoryMatchTimer = setTimeout(() => {
    categoryMatchTimer = null
    void matchCategoryByDeviceName(deviceName)
  }, 400)
}

watch(
  () => props.model.device_name,
  (name, oldName) => {
    if (isView.value) return
    if (oldName === name) return
    scheduleCategoryMatch(String(name ?? ''))
  }
)

watch(
  () => props.model.category_id,
  (_id, oldId) => {
    if (oldId === undefined) return
    if (applyingAutoCategory) return
    categoryTouchedByUser.value = true
  }
)

watch(
  () => [props.mode, props.model.id] as const,
  () => {
    categoryTouchedByUser.value = false
    applyingAutoCategory = false
  }
)

async function prepareBeforeSave() {
  if (categoryMatchTimer != null) {
    clearTimeout(categoryMatchTimer)
    categoryMatchTimer = null
  }
  await matchCategoryByDeviceName(String(props.model.device_name ?? ''))
}

if (crudBeforeSave) {
  crudBeforeSave.register(prepareBeforeSave)
  onUnmounted(() => crudBeforeSave.unregister(prepareBeforeSave))
}

defineExpose({ prepareBeforeSave })

const basicFields = computed(() => {
  const source = props.fields?.length ? props.fields : getSchema('medical_device')
  return source
    .filter((f) => basicGroupKeys.has(f.group ?? 'other') && f.form !== false)
    .map((f) => {
      const lockedCode = f.prop === 'device_code' && !isCreate.value
      return {
        ...f,
        readonly: isView.value || lockedCode || !!f.readonly
      }
    })
})

const repairColumns: RecordColumn[] = [
  { prop: 'wo_no', label: '工单号', minWidth: 140 },
  { prop: 'fault_desc', label: '故障描述', minWidth: 180 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'engineer_name', label: '工程师', minWidth: 120 },
  { prop: 'report_time', label: '报修时间', minWidth: 160 }
]

const maintainColumns: RecordColumn[] = [
  { prop: 'record_no', label: '记录号', minWidth: 140 },
  { prop: 'plan_name', label: '计划名称', minWidth: 160 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'maintain_date', label: '保养日期', minWidth: 140 },
  { prop: 'engineer_name', label: '执行人', minWidth: 120 }
]

const inspectionColumns: RecordColumn[] = [
  { prop: 'inspection_no', label: '巡检单号', minWidth: 140 },
  { prop: 'inspection_type', label: '巡检类型', minWidth: 120 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'inspector_name', label: '巡检人', minWidth: 120 },
  { prop: 'inspection_date', label: '巡检日期', minWidth: 140 }
]

const metrologyColumns: RecordColumn[] = [
  { prop: 'metrology_no', label: '计量编号', minWidth: 140 },
  { prop: 'metrology_type', label: '计量类型', minWidth: 120 },
  { prop: 'result', label: '计量结果', minWidth: 120 },
  { prop: 'org_name', label: '计量机构', minWidth: 140 },
  { prop: 'metrology_date', label: '计量日期', minWidth: 140 }
]

const inventoryColumns: RecordColumn[] = [
  { prop: 'check_no', label: '盘点单号', minWidth: 140 },
  { prop: 'check_type', label: '盘点类型', minWidth: 120 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'dept_name', label: '盘点科室', minWidth: 140 },
  { prop: 'check_date', label: '盘点日期', minWidth: 140 }
]

const sharedLoanColumns: RecordColumn[] = [
  { prop: 'loan_no', label: '借调单号', minWidth: 140 },
  { prop: 'to_dept_name', label: '借入科室', minWidth: 120 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'fee_mode', label: '计费方式', minWidth: 100 },
  { prop: 'fee_unit_price', label: '单价', minWidth: 90 },
  { prop: 'loan_start', label: '计划开始', minWidth: 120 },
  { prop: 'loan_end', label: '计划结束', minWidth: 120 }
]

const sharedFeeColumns: RecordColumn[] = [
  { prop: 'fee_no', label: '收费单号', minWidth: 140 },
  { prop: 'loan_no', label: '借调单号', minWidth: 140 },
  { prop: 'fee_amount', label: '金额', minWidth: 100 },
  { prop: 'fee_date', label: '收费日期', minWidth: 120 },
  { prop: 'paid_status', label: '状态', minWidth: 100 }
]

const adverseColumns: RecordColumn[] = [
  { prop: 'event_no', label: '事件编号', minWidth: 140 },
  { prop: 'severity_level', label: '严重等级', minWidth: 120 },
  { prop: 'event_type', label: '事件类型', minWidth: 120 },
  { prop: 'status', label: '处理状态', minWidth: 100 },
  { prop: 'report_time', label: '上报时间', minWidth: 160 }
]
</script>

<style scoped>
.device-ledger-form {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.device-ledger-form :deep(.form-tab-nav) {
  flex-shrink: 0;
}

.device-ledger-form__panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
  padding-bottom: 8px;
}

.device-ledger-form__card-pane {
  padding-top: 4px;
}

.device-ledger-form--view :deep(.el-input__wrapper),
.device-ledger-form--view :deep(.el-select__wrapper),
.device-ledger-form--view :deep(.el-input-number .el-input__wrapper),
.device-ledger-form--view :deep(.el-date-editor .el-input__wrapper),
.device-ledger-form :deep(.el-input.is-disabled .el-input__wrapper),
.device-ledger-form :deep(.el-select__wrapper.is-disabled),
.device-ledger-form :deep(.el-input-number.is-disabled .el-input__wrapper),
.device-ledger-form :deep(.el-date-editor.is-disabled .el-input__wrapper) {
  background-color: #fff !important;
  cursor: default;
}

.device-ledger-form--view :deep(.el-input__inner),
.device-ledger-form--view :deep(.el-select__selected-item),
.device-ledger-form--view :deep(.el-input-number .el-input__inner),
.device-ledger-form :deep(.el-input.is-disabled .el-input__inner),
.device-ledger-form :deep(.el-select__wrapper.is-disabled .el-select__selected-item),
.device-ledger-form :deep(.el-input-number.is-disabled .el-input__inner) {
  color: var(--el-text-color-regular) !important;
  -webkit-text-fill-color: var(--el-text-color-regular);
}
</style>
