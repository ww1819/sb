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
        <DeviceAssetCard :model="model" :device-id="deviceId" />
      </div>

      <DeviceArchivePanel v-show="activeTab === 'archive'" :readonly="isView" />
      <DeviceImagePanel v-show="activeTab === 'images'" :readonly="isView" />
      <DevicePowerMonitorPanel
        v-show="activeTab === 'power_monitor'"
        :device-id="deviceId"
        :device-code="String(model.device_code ?? '')"
        :device-name="String(model.device_name ?? '')"
        :model="model"
        :readonly="isView"
      />
      <DeviceWarrantyPanel
        v-show="activeTab === 'warranty'"
        :device-id="deviceId"
        :device-code="String(model.device_code ?? '')"
        :device-name="String(model.device_name ?? '')"
        :manufacturer-name="String(model.manufacturer_name ?? '')"
        :readonly="isView"
      />

      <DeviceLabelPanel
        v-show="activeTab === 'label'"
        :device-id="deviceId"
        :device-code="String(model.device_code ?? '')"
        :device-name="String(model.device_name ?? '')"
      />

      <DeviceRecordTablePanel
        v-show="activeTab === 'repair'"
        :columns="repairColumns"
        empty-text="暂无维修记录"
        filter-placeholder="工单号 / 故障描述"
        load-url="/repair/workorder/page"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'maintain'"
        :columns="opsExecColumns"
        empty-text="暂无保养记录"
        filter-placeholder="执行单号 / 计划单号"
        load-url="/maintain/device/{deviceId}/executions"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'maintain_plan'"
        :columns="opsPlanColumns"
        empty-text="暂无保养计划"
        filter-placeholder="计划单号 / 计划名称"
        load-url="/maintain/device/{deviceId}/plans"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inspection'"
        :columns="opsExecColumns"
        empty-text="暂无巡检记录"
        filter-placeholder="执行单号 / 计划单号"
        load-url="/inspect/device/{deviceId}/executions"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inspection_plan'"
        :columns="opsPlanColumns"
        empty-text="暂无巡检计划"
        filter-placeholder="计划单号 / 计划名称"
        load-url="/inspect/device/{deviceId}/plans"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'metrology'"
        :columns="metrologyColumns"
        empty-text="暂无计量记录"
        filter-placeholder="执行单号 / 证书号"
        load-url="/metrology/query/page"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'metrology_plan'"
        :columns="metrologyPlanColumns"
        empty-text="暂无计量计划"
        filter-placeholder="计划编号 / 计划名称"
        load-url="/metrology/device/{deviceId}/plans"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'pm'"
        :columns="opsExecColumns"
        empty-text="暂无PM维护记录"
        filter-placeholder="执行单号 / 计划单号"
        load-url="/pm/device/{deviceId}/executions"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'pm_plan'"
        :columns="opsPlanColumns"
        empty-text="暂无PM维护计划"
        filter-placeholder="计划单号 / 计划名称"
        load-url="/pm/device/{deviceId}/plans"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'shared_loan'"
        :columns="sharedLoanColumns"
        empty-text="暂无借调记录"
        filter-placeholder="借调单号"
        load-url="/shared/loan/page"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'shared_fee'"
        :columns="sharedFeeColumns"
        empty-text="暂无借调费用"
        filter-placeholder="收费单号"
        load-url="/shared/fee/page"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inventory'"
        :columns="inventoryColumns"
        empty-text="暂无盘点记录"
        filter-placeholder="盘点单号"
        load-url="/asset/inventory/by-device/{deviceId}"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'adverse'"
        :columns="adverseColumns"
        empty-text="暂无不良事件"
        filter-placeholder="事件编号"
        load-url="/qc/adverse/page"
        :device-id="deviceId"
      />
      <DeviceCurrentReadingPanel
        v-show="activeTab === 'current'"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'current_bind'"
        :columns="currentBindColumns"
        empty-text="暂无电流标签绑定记录"
        filter-placeholder="标签编码 / 备注"
        load-url="/power/device/{deviceId}/bind-log"
        :device-id="deviceId"
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
import DevicePowerMonitorPanel from '@/components/asset/tabs/DevicePowerMonitorPanel.vue'
import DeviceWarrantyPanel from '@/components/asset/tabs/DeviceWarrantyPanel.vue'
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
const deviceId = computed(() => String(props.model.id ?? ''))

type CrudBeforeSaveApi = {
  register: (fn: () => void | Promise<void>) => void
  unregister: (fn: () => void | Promise<void>) => void
}
const crudBeforeSave = inject<CrudBeforeSaveApi | null>('crudBeforeSave', null)

/** AST-UI-14 / 附录 P.2：查看态完整顺序；编辑态见 AST-UI-17 */
const allTabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'card', label: '资产卡片' },
  { key: 'archive', label: '设备档案' },
  { key: 'images', label: '设备图片' },
  { key: 'power_monitor', label: '电流监测' },
  { key: 'warranty', label: '维保信息' },
  { key: 'label', label: '资产标签' },
  { key: 'repair', label: '维修记录' },
  { key: 'maintain', label: '保养记录' },
  { key: 'maintain_plan', label: '保养计划' },
  { key: 'inspection', label: '巡检记录' },
  { key: 'inspection_plan', label: '巡检计划' },
  { key: 'metrology', label: '计量记录' },
  { key: 'metrology_plan', label: '计量计划' },
  { key: 'pm', label: 'PM维护记录' },
  { key: 'pm_plan', label: 'PM维护计划' },
  { key: 'shared_loan', label: '借调记录' },
  { key: 'shared_fee', label: '借调费用' },
  { key: 'inventory', label: '盘点记录' },
  { key: 'adverse', label: '不良事件' },
  { key: 'current', label: '电流读数' },
  { key: 'current_bind', label: '电流标签绑定记录' }
]

/** AST-UI-17 / AST-WRN-01：编辑/新增露出电流监测与维保信息；查看态保留完整业务 Tab（含维保，不含编辑专用 power_monitor） */
const ledgerKeys = new Set(['basic', 'archive', 'images', 'power_monitor', 'warranty'])

const visibleTabs = computed(() => {
  if (isView.value) {
    return allTabs.filter((t) => t.key !== 'power_monitor')
  }
  return allTabs.filter((t) => ledgerKeys.has(t.key))
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
  { prop: 'fault_description', label: '故障描述', minWidth: 180 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'assigned_user_name', label: '工程师', minWidth: 120 },
  { prop: 'report_time', label: '报修时间', minWidth: 160 }
]

const opsExecColumns: RecordColumn[] = [
  { prop: 'execution_no', label: '执行单号', minWidth: 140 },
  { prop: 'plan_no', label: '计划单号', minWidth: 140 },
  { prop: 'execution_status', label: '执行状态', minWidth: 100 },
  { prop: 'status', label: '明细状态', minWidth: 100 },
  { prop: 'planned_date', label: '计划日期', minWidth: 120 }
]

const opsPlanColumns: RecordColumn[] = [
  { prop: 'plan_no', label: '计划单号', minWidth: 140 },
  { prop: 'plan_name', label: '计划名称', minWidth: 160 },
  { prop: 'approval_status', label: '审核状态', minWidth: 100 },
  { prop: 'plan_status', label: '计划状态', minWidth: 100 },
  { prop: 'next_due_date', label: '下次到期', minWidth: 120 }
]

const metrologyColumns: RecordColumn[] = [
  { prop: 'execution_no', label: '执行单号', minWidth: 140 },
  { prop: 'template_name', label: '模板', minWidth: 140 },
  { prop: 'overall_result', label: '计量结果', minWidth: 100 },
  { prop: 'org_name', label: '计量机构', minWidth: 140 },
  { prop: 'completed_at', label: '完成时间', minWidth: 160 }
]

const metrologyPlanColumns: RecordColumn[] = [
  { prop: 'plan_code', label: '计划编号', minWidth: 140 },
  { prop: 'plan_name', label: '计划名称', minWidth: 160 },
  { prop: 'approval_status', label: '审核状态', minWidth: 100 },
  { prop: 'status', label: '状态', minWidth: 100 },
  { prop: 'next_due_date', label: '下次到期', minWidth: 120 },
  { prop: 'org_name', label: '计量机构', minWidth: 140 }
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

const currentBindColumns: RecordColumn[] = [
  { prop: 'tag_code', label: '标签编码', minWidth: 120 },
  { prop: 'tag_name', label: '标签名称', minWidth: 140 },
  { prop: 'bound_at', label: '绑定开始', minWidth: 160 },
  { prop: 'unbound_at', label: '绑定结束', minWidth: 160 },
  { prop: 'remark', label: '备注', minWidth: 120 }
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
