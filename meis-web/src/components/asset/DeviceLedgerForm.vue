<template>
  <el-form
    label-width="96px"
    class="device-ledger-form"
    :class="{ 'device-ledger-form--view': isView, 'device-ledger-form--side-nav': isView }"
  >
    <FormTabNav v-model="activeTab" :tabs="visibleTabs" :layout="isView ? 'side' : 'top'" />

    <div class="device-ledger-form__panel">
      <div v-show="activeTab === 'basic'">
        <div v-if="!isCreate && !isView" class="device-ledger-form__own-actions">
          <el-button type="primary" plain @click="openChangeDept">变更所属科室</el-button>
          <el-button type="primary" plain @click="openChangeLocation">变更安装/存放位置</el-button>
          <span class="device-ledger-form__own-hint">科室与楼层/房间请用按钮变更；勿在表单中静默修改。</span>
        </div>
        <GroupedFormFields
          table="medical_device"
          :model="model"
          :fields="basicFields"
          :group-columns="{ basic: 5, finance: 5, location: 5, vendor: 5, time: 5, accounting: 5, status: 5, compliance: 5, other: 5 }"
          :group-rows="{ basic: basicFormRows, vendor: vendorFormRows, accounting: accountingFormRows, location: locationFormRows }"
          :group-panels="{ basic: basicFormPanel, status: statusFormPanel }"
          :highlight-labels="highlightLabels"
          :group-titles="{ finance: '折旧信息', time: '合同信息', accounting: '财务信息', status: '设备属性', compliance: '动态监测' }"
        />
      </div>

      <div v-show="activeTab === 'card'" class="device-ledger-form__card-pane">
        <DeviceAssetCard :model="model" :device-id="deviceId" />
      </div>

      <DeviceArchivePanel v-show="activeTab === 'archive'" :device-id="deviceId" :readonly="isView" />
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
      <DeviceOwnershipBackfillPanel
        v-show="activeTab === 'ownership_backfill'"
        :device-id="deviceId"
      />
      <DevicePartReplacementPanel
        v-show="activeTab === 'part_replace_edit'"
        :device-id="deviceId"
      />

      <DeviceLabelPanel
        v-show="activeTab === 'label'"
        :device-id="deviceId"
        :device-code="String(model.device_code ?? '')"
        :device-name="String(model.device_name ?? '')"
      />

      <DeviceLicensePanel
        v-show="activeTab === 'license'"
        :device-id="deviceId"
        :readonly="isView"
      />
      <DeviceTrainingAuthPanel
        v-show="activeTab === 'training_auth'"
        :device-id="deviceId"
        :readonly="isView"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'udi_hist'"
        :columns="udiHistColumns"
        empty-text="暂无UDI变更历史"
        filter-placeholder="UDI"
        load-url="/asset/device-udi-history/by-device/{deviceId}"
        :device-id="deviceId"
      />

      <DeviceRecordTablePanel
        v-show="activeTab === 'repair'"
        :columns="repairColumns"
        empty-text="暂无维修记录"
        filter-placeholder="工单号 / 故障描述"
        load-url="/repair/workorder/page"
        :device-id="deviceId"
        :drill="repairDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'spare_replace'"
        :columns="spareReplaceColumns"
        empty-text="暂无配件更换记录"
        filter-placeholder="配件编码 / 名称 / 工单号"
        load-url="/asset/device/{deviceId}/spare-replacements"
        :device-id="deviceId"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'maintain'"
        :columns="maintainExecColumns"
        empty-text="暂无保养记录"
        filter-placeholder="执行单号 / 计划单号"
        load-url="/maintain/device/{deviceId}/executions"
        :device-id="deviceId"
        :drill="maintainExecDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'maintain_plan'"
        :columns="opsPlanColumns"
        empty-text="暂无保养计划"
        filter-placeholder="计划单号 / 计划名称"
        load-url="/maintain/device/{deviceId}/plans"
        :device-id="deviceId"
        :drill="maintainPlanDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inspection'"
        :columns="inspectExecColumns"
        empty-text="暂无巡检记录"
        filter-placeholder="执行单号 / 计划单号"
        load-url="/inspect/device/{deviceId}/executions"
        :device-id="deviceId"
        :drill="inspectExecDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'inspection_plan'"
        :columns="opsPlanColumns"
        empty-text="暂无巡检计划"
        filter-placeholder="计划单号 / 计划名称"
        load-url="/inspect/device/{deviceId}/plans"
        :device-id="deviceId"
        :drill="inspectPlanDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'metrology'"
        :columns="metrologyColumns"
        empty-text="暂无计量记录"
        filter-placeholder="执行单号 / 证书号"
        load-url="/metrology/query/page"
        :device-id="deviceId"
        :drill="metrologyExecDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'metrology_plan'"
        :columns="metrologyPlanColumns"
        empty-text="暂无计量计划"
        filter-placeholder="计划编号 / 计划名称"
        load-url="/metrology/device/{deviceId}/plans"
        :device-id="deviceId"
        :drill="metrologyPlanDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'pm'"
        :columns="pmExecColumns"
        empty-text="暂无PM维护记录"
        filter-placeholder="执行单号 / 计划单号"
        load-url="/pm/device/{deviceId}/executions"
        :device-id="deviceId"
        :drill="pmExecDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'pm_plan'"
        :columns="opsPlanColumns"
        empty-text="暂无PM维护计划"
        filter-placeholder="计划单号 / 计划名称"
        load-url="/pm/device/{deviceId}/plans"
        :device-id="deviceId"
        :drill="pmPlanDrill"
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
        :drill="inventoryDrill"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'ownership'"
        :columns="ownershipColumns"
        empty-text="暂无归属历史"
        filter-placeholder="科室 / 仓库 / 单号"
        load-url="/asset/ownership-period/by-device/{deviceId}"
        :device-id="deviceId"
        :extra-params="{ include_draft: false }"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'location_hist'"
        :columns="locationHistColumns"
        empty-text="暂无位置历史"
        filter-placeholder="楼层 / 房间"
        load-url="/asset/ownership-period/location/by-device/{deviceId}"
        :device-id="deviceId"
        :extra-params="{ include_draft: false }"
      />
      <DeviceRecordTablePanel
        v-show="activeTab === 'disposition'"
        :columns="dispositionColumns"
        empty-text="暂无处置记录"
        filter-placeholder="单号 / 类型"
        load-url="/asset/device/{deviceId}/dispositions"
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

    <AppModal v-model="deptDialogVisible" title="变更所属科室" size="sm">
      <el-form label-width="96px">
        <el-form-item label="所属科室" required>
          <RefSelect v-model="deptForm.dept_id" link-table="department" placeholder="选择科室" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ownSaving" @click="submitChangeDept">确定</el-button>
      </template>
    </AppModal>

    <AppModal v-model="locDialogVisible" title="变更安装/存放位置" size="sm">
      <el-form label-width="96px">
        <el-form-item label="楼层">
          <el-input v-model="locForm.location_floor" clearable />
        </el-form-item>
        <el-form-item label="房间">
          <el-input v-model="locForm.room_number" clearable />
        </el-form-item>
        <el-form-item label="位置详情">
          <el-input v-model="locForm.location_detail" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ownSaving" @click="submitChangeLocation">确定</el-button>
      </template>
    </AppModal>
  </el-form>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import FormTabNav from '@/components/form/FormTabNav.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import AppModal from '@/components/AppModal.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import DeviceAssetCard from '@/components/asset/DeviceAssetCard.vue'
import DeviceArchivePanel from '@/components/asset/tabs/DeviceArchivePanel.vue'
import DeviceImagePanel from '@/components/asset/tabs/DeviceImagePanel.vue'
import DevicePowerMonitorPanel from '@/components/asset/tabs/DevicePowerMonitorPanel.vue'
import DeviceWarrantyPanel from '@/components/asset/tabs/DeviceWarrantyPanel.vue'
import DeviceOwnershipBackfillPanel from '@/components/asset/tabs/DeviceOwnershipBackfillPanel.vue'
import DeviceLicensePanel from '@/components/asset/tabs/DeviceLicensePanel.vue'
import DeviceTrainingAuthPanel from '@/components/asset/tabs/DeviceTrainingAuthPanel.vue'
import DevicePartReplacementPanel from '@/components/asset/tabs/DevicePartReplacementPanel.vue'
import DeviceRecordTablePanel from '@/components/asset/tabs/DeviceRecordTablePanel.vue'
import DeviceCurrentReadingPanel from '@/components/asset/tabs/DeviceCurrentReadingPanel.vue'
import DeviceLabelPanel from '@/components/asset/tabs/DeviceLabelPanel.vue'
import type {
  RecordColumn,
  SheetDrillConfig
} from '@/components/asset/tabs/DeviceRecordTablePanel.vue'
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

/** 编辑态 Tab（少而精）；查看态顺序见 viewTabOrder（现场操作习惯） */
const editTabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'archive', label: '设备档案' },
  { key: 'images', label: '设备图片' },
  { key: 'power_monitor', label: '电流监测' },
  { key: 'warranty', label: '维保信息' },
  { key: 'license', label: '设备证照' },
  { key: 'training_auth', label: '培训授权' },
  { key: 'ownership_backfill', label: '补录归属历史' },
  { key: 'part_replace_edit', label: '非维修配件更换' }
]

/** AST-UI-24：查看态左侧导航顺序——台账核对 → 证照合规 → 运维闭环 → 流转处置 → 监测 */
const viewTabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'card', label: '资产卡片' },
  { key: 'license', label: '设备证照' },
  { key: 'training_auth', label: '培训授权' },
  { key: 'udi_hist', label: 'UDI历史' },
  { key: 'archive', label: '设备档案' },
  { key: 'images', label: '设备图片' },
  { key: 'warranty', label: '维保信息' },
  { key: 'label', label: '资产标签' },
  { key: 'repair', label: '维修记录' },
  { key: 'spare_replace', label: '配件更换记录' },
  { key: 'maintain', label: '保养记录' },
  { key: 'maintain_plan', label: '保养计划' },
  { key: 'inspection', label: '巡检记录' },
  { key: 'inspection_plan', label: '巡检计划' },
  { key: 'metrology', label: '计量记录' },
  { key: 'metrology_plan', label: '计量计划' },
  { key: 'pm', label: 'PM维护记录' },
  { key: 'pm_plan', label: 'PM维护计划' },
  { key: 'inventory', label: '盘点记录' },
  { key: 'ownership', label: '归属历史' },
  { key: 'location_hist', label: '位置历史' },
  { key: 'disposition', label: '处置记录' },
  { key: 'shared_loan', label: '借调记录' },
  { key: 'shared_fee', label: '借调费用' },
  { key: 'adverse', label: '不良事件' },
  { key: 'current', label: '电流读数' },
  { key: 'current_bind', label: '电流标签绑定记录' }
]

const visibleTabs = computed(() => (isView.value ? viewTabs : editTabs))

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
  ['asset_category_id', 'finance_category_id', 'eq_class', 'criticality', 'acquisition_mode'],
  ['standby_current_max_ma', 'standby_current_min_ma', 'country_of_origin'],
  ['clinical_owner_user_id', 'use_dept_head', 'dept_id', 'asset_manager_user_id', 'manage_dept_head'],
  ['manage_dept_id', 'registration_no', 'udi_di', 'udi_pi', 'production_date'],
  ['gs1_gtin', 'lot_no']
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
  ['location_detail', 'ip_address', 'mac_address', 'energy_class']
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

const ownershipLockedProps = new Set(['dept_id', 'location_floor', 'room_number', 'location_detail'])

const basicFields = computed(() => {
  const source = props.fields?.length ? props.fields : getSchema('medical_device')
  return source
    .filter((f) => basicGroupKeys.has(f.group ?? 'other') && f.form !== false)
    .map((f) => {
      const lockedCode = f.prop === 'device_code' && !isCreate.value
      const lockedOwn = !isCreate.value && ownershipLockedProps.has(f.prop)
      return {
        ...f,
        readonly: isView.value || lockedCode || lockedOwn || !!f.readonly
      }
    })
})

const repairColumns: RecordColumn[] = [
  { prop: 'wo_no', label: '工单号', minWidth: 140, link: true },
  { prop: 'fault_description', label: '故障描述', minWidth: 180 },
  { prop: 'status', label: '状态', minWidth: 100, dictType: 'wo_status' },
  { prop: 'assigned_user_name', label: '工程师', minWidth: 120 },
  { prop: 'report_time', label: '报修时间', minWidth: 160 }
]

const spareReplaceColumns: RecordColumn[] = [
  { prop: 'replaced_at', label: '更换时间', minWidth: 170 },
  { prop: 'part_code', label: '配件编码', minWidth: 120 },
  { prop: 'part_name', label: '配件名称', minWidth: 140 },
  { prop: 'part_specification', label: '规格', minWidth: 110 },
  { prop: 'quantity', label: '数量', minWidth: 80 },
  { prop: 'unit_price', label: '单价', minWidth: 90 },
  { prop: 'total_price', label: '金额', minWidth: 90 },
  { prop: 'supplier_name', label: '供应商', minWidth: 120 },
  { prop: 'workorder_no', label: '工单号', minWidth: 130 },
  { prop: 'process_type_name', label: '进程类型', minWidth: 100 },
  { prop: 'remark', label: '备注', minWidth: 120 },
  { prop: 'source_mode', label: '生成方式', minWidth: 110, dictType: 'source_mode' }
]

function opsExecColumns(execDict: string, itemDict: string, resultDict = 'maintain_result'): RecordColumn[] {
  return [
    { prop: 'execution_no', label: '执行单号', minWidth: 140, link: true },
    { prop: 'plan_no', label: '计划单号', minWidth: 140 },
    { prop: 'execution_status', label: '执行状态', minWidth: 100, dictType: execDict },
    { prop: 'status', label: '明细状态', minWidth: 100, dictType: itemDict },
    { prop: 'overall_result', label: '结果', minWidth: 90, dictType: resultDict },
    { prop: 'planned_date', label: '计划日期', minWidth: 120 }
  ]
}

const maintainExecColumns = opsExecColumns('maintain_exec_status', 'maintain_exec_item_status')
const inspectExecColumns = opsExecColumns('inspect_exec_status', 'inspect_exec_item_status')
const pmExecColumns = opsExecColumns('pm_exec_status', 'pm_exec_item_status')

const opsPlanColumns: RecordColumn[] = [
  { prop: 'plan_no', label: '计划单号', minWidth: 140, link: true },
  { prop: 'plan_name', label: '计划名称', minWidth: 160 },
  { prop: 'approval_status', label: '审核状态', minWidth: 100, dictType: 'approval_status' },
  { prop: 'plan_status', label: '计划状态', minWidth: 100, dictType: 'plan_status' },
  { prop: 'next_due_date', label: '下次到期', minWidth: 120 }
]

const metrologyColumns: RecordColumn[] = [
  { prop: 'execution_no', label: '执行单号', minWidth: 140, link: true },
  { prop: 'template_name', label: '模板', minWidth: 140 },
  { prop: 'overall_result', label: '计量结果', minWidth: 100, dictType: 'metrology_result' },
  { prop: 'org_name', label: '计量机构', minWidth: 140 },
  { prop: 'completed_at', label: '完成时间', minWidth: 160 }
]

const metrologyPlanColumns: RecordColumn[] = [
  { prop: 'plan_code', label: '计划编号', minWidth: 140, link: true },
  { prop: 'plan_name', label: '计划名称', minWidth: 160 },
  { prop: 'approval_status', label: '审核状态', minWidth: 100, dictType: 'approval_status' },
  { prop: 'status', label: '状态', minWidth: 100, dictType: 'plan_status' },
  { prop: 'next_due_date', label: '下次到期', minWidth: 120 },
  { prop: 'org_name', label: '计量机构', minWidth: 140 }
]

const inventoryColumns: RecordColumn[] = [
  { prop: 'check_no', label: '盘点单号', minWidth: 140, link: true },
  { prop: 'check_type', label: '盘点类型', minWidth: 120, dictType: 'check_type' },
  { prop: 'status', label: '状态', minWidth: 100, dictType: 'check_status' },
  { prop: 'dept_name', label: '盘点科室', minWidth: 140 },
  { prop: 'check_date', label: '盘点日期', minWidth: 140 }
]

const sharedLoanColumns: RecordColumn[] = [
  { prop: 'loan_no', label: '借调单号', minWidth: 140 },
  { prop: 'to_dept_name', label: '借入科室', minWidth: 120 },
  { prop: 'status', label: '状态', minWidth: 100, dictType: 'loan_status' },
  { prop: 'fee_mode', label: '计费方式', minWidth: 100, dictType: 'shared_fee_mode' },
  { prop: 'fee_unit_price', label: '单价', minWidth: 90 },
  { prop: 'loan_start', label: '计划开始', minWidth: 120 },
  { prop: 'loan_end', label: '计划结束', minWidth: 120 }
]

const sharedFeeColumns: RecordColumn[] = [
  { prop: 'fee_no', label: '收费单号', minWidth: 140 },
  { prop: 'loan_no', label: '借调单号', minWidth: 140 },
  { prop: 'fee_amount', label: '金额', minWidth: 100 },
  { prop: 'fee_date', label: '收费日期', minWidth: 120 },
  { prop: 'paid_status', label: '状态', minWidth: 100, dictType: 'paid_status' }
]

const repairDrill: SheetDrillConfig = {
  detailUrl: '/repair/workorder/{id}',
  idProp: 'id',
  titleProp: 'wo_no',
  titlePrefix: '维修工单',
  kind: 'repair'
}
const maintainExecDrill: SheetDrillConfig = {
  detailUrl: '/maintain/execution/{id}',
  idProp: 'execution_id',
  titleProp: 'execution_no',
  titlePrefix: '保养执行单',
  kind: 'execution',
  itemStatusDict: 'maintain_exec_item_status',
  resultDict: 'maintain_result'
}
const inspectExecDrill: SheetDrillConfig = {
  detailUrl: '/inspect/execution/{id}',
  idProp: 'execution_id',
  titleProp: 'execution_no',
  titlePrefix: '巡检执行单',
  kind: 'execution',
  itemStatusDict: 'inspect_exec_item_status',
  resultDict: 'maintain_result'
}
const pmExecDrill: SheetDrillConfig = {
  detailUrl: '/pm/execution/{id}',
  idProp: 'execution_id',
  titleProp: 'execution_no',
  titlePrefix: 'PM执行单',
  kind: 'execution',
  itemStatusDict: 'pm_exec_item_status',
  resultDict: 'maintain_result'
}
const metrologyExecDrill: SheetDrillConfig = {
  detailUrl: '/metrology/query/{id}',
  idProp: 'id',
  titleProp: 'execution_no',
  titlePrefix: '计量执行',
  kind: 'item',
  itemStatusDict: 'metrology_exec_item_status',
  resultDict: 'metrology_result'
}
const maintainPlanDrill: SheetDrillConfig = {
  detailUrl: '/maintain/plan/{id}',
  idProp: 'plan_id',
  titleProp: 'plan_no',
  titlePrefix: '保养计划',
  kind: 'plan'
}
const inspectPlanDrill: SheetDrillConfig = {
  detailUrl: '/inspect/plan/{id}',
  idProp: 'plan_id',
  titleProp: 'plan_no',
  titlePrefix: '巡检计划',
  kind: 'plan'
}
const pmPlanDrill: SheetDrillConfig = {
  detailUrl: '/pm/plan/{id}',
  idProp: 'plan_id',
  titleProp: 'plan_no',
  titlePrefix: 'PM计划',
  kind: 'plan'
}
const metrologyPlanDrill: SheetDrillConfig = {
  detailUrl: '/metrology/plan/{id}',
  idProp: 'id',
  titleProp: 'plan_code',
  titlePrefix: '计量计划',
  kind: 'plan'
}
const inventoryDrill: SheetDrillConfig = {
  detailUrl: '/asset/inventory/{id}',
  idProp: 'id',
  titleProp: 'check_no',
  titlePrefix: '盘点单',
  kind: 'inventory'
}

const adverseColumns: RecordColumn[] = [
  { prop: 'event_no', label: '事件编号', minWidth: 140 },
  { prop: 'severity_level', label: '严重等级', minWidth: 120, dictType: 'adverse_severity' },
  { prop: 'event_type', label: '事件类型', minWidth: 120, dictType: 'adverse_event_type' },
  { prop: 'status', label: '处理状态', minWidth: 100, dictType: 'adverse_status' },
  { prop: 'report_time', label: '上报时间', minWidth: 160 }
]

const udiHistColumns: RecordColumn[] = [
  { prop: 'udi_di', label: 'UDI-DI', minWidth: 140 },
  { prop: 'udi_pi', label: 'UDI-PI', minWidth: 140 },
  { prop: 'effective_from', label: '开始', minWidth: 160 },
  { prop: 'effective_to', label: '结束', minWidth: 160 },
  { prop: 'change_reason', label: '原因', minWidth: 120, dictType: 'udi_change_reason' },
  { prop: 'remark', label: '备注', minWidth: 120 }
]

const ownershipColumns: RecordColumn[] = [
  { prop: 'campus_name', label: '院区', minWidth: 100 },
  { prop: 'owner_type', label: '归属类型', minWidth: 100, dictType: 'owner_type' },
  { prop: 'warehouse_name', label: '仓库', minWidth: 120 },
  { prop: 'dept_name', label: '科室', minWidth: 120 },
  { prop: 'effective_from', label: '开始', minWidth: 160 },
  { prop: 'effective_to', label: '结束', minWidth: 160 },
  { prop: 'source_biz_no', label: '来源单号', minWidth: 130 },
  { prop: 'change_reason', label: '原因', minWidth: 110, dictType: 'ownership_change_reason' },
  { prop: 'confirm_status', label: '确认状态', minWidth: 100, dictType: 'confirm_status' },
  { prop: 'source_mode', label: '生成方式', minWidth: 110, dictType: 'source_mode' }
]

const locationHistColumns: RecordColumn[] = [
  { prop: 'location_floor', label: '楼层', minWidth: 90 },
  { prop: 'room_number', label: '房间', minWidth: 90 },
  { prop: 'location_detail', label: '位置详情', minWidth: 140 },
  { prop: 'effective_from', label: '开始', minWidth: 160 },
  { prop: 'effective_to', label: '结束', minWidth: 160 },
  { prop: 'change_reason', label: '原因', minWidth: 110, dictType: 'ownership_change_reason' },
  { prop: 'confirm_status', label: '确认状态', minWidth: 100, dictType: 'confirm_status' },
  { prop: 'source_mode', label: '生成方式', minWidth: 110, dictType: 'source_mode' }
]

const dispositionColumns: RecordColumn[] = [
  { prop: 'occurred_at', label: '发生时间', minWidth: 160 },
  { prop: 'disposition_type_label', label: '类型', minWidth: 100 },
  { prop: 'biz_no', label: '单号', minWidth: 140 },
  { prop: 'biz_status', label: '单据状态', minWidth: 100, dictType: 'disposition_biz_status' },
  { prop: 'remark', label: '备注', minWidth: 140 },
  { prop: 'source_mode', label: '生成方式', minWidth: 110, dictType: 'source_mode' }
]

const currentBindColumns: RecordColumn[] = [
  { prop: 'tag_code', label: '标签编码', minWidth: 120 },
  { prop: 'tag_name', label: '标签名称', minWidth: 140 },
  { prop: 'bound_at', label: '绑定开始', minWidth: 160 },
  { prop: 'unbound_at', label: '绑定结束', minWidth: 160 },
  { prop: 'remark', label: '备注', minWidth: 120 }
]

const deptDialogVisible = ref(false)
const locDialogVisible = ref(false)
const ownSaving = ref(false)
const deptForm = reactive({ dept_id: '' })
const locForm = reactive({ location_floor: '', room_number: '', location_detail: '' })

function openChangeDept() {
  deptForm.dept_id = props.model.dept_id ? String(props.model.dept_id) : ''
  deptDialogVisible.value = true
}

function openChangeLocation() {
  locForm.location_floor = String(props.model.location_floor ?? '')
  locForm.room_number = String(props.model.room_number ?? '')
  locForm.location_detail = String(props.model.location_detail ?? '')
  locDialogVisible.value = true
}

async function pickChangeMode(): Promise<'transfer' | 'correct' | null> {
  try {
    await ElMessageBox.confirm(
      '请选择变更性质：「真变更」将生成历史记录；「纠错」仅修正台账当前值，不新开历史段。',
      '确认变更性质',
      {
        distinguishCancelAndClose: true,
        confirmButtonText: '真变更（生成历史）',
        cancelButtonText: '纠错（不生成历史）',
        type: 'warning'
      }
    )
    return 'transfer'
  } catch (action) {
    if (action === 'cancel') return 'correct'
    return null
  }
}

async function submitChangeDept() {
  if (!deviceId.value || !deptForm.dept_id) {
    ElMessage.warning('请选择所属科室')
    return
  }
  const mode = await pickChangeMode()
  if (!mode) return
  ownSaving.value = true
  try {
    await http.post(`/asset/ownership-period/device/${deviceId.value}/change-dept`, {
      dept_id: deptForm.dept_id,
      mode
    })
    props.model.dept_id = deptForm.dept_id
    props.model.warehouse_id = mode === 'transfer' ? null : props.model.warehouse_id
    ElMessage.success(mode === 'transfer' ? '已真变更所属科室' : '已纠错所属科室')
    deptDialogVisible.value = false
  } finally {
    ownSaving.value = false
  }
}

async function submitChangeLocation() {
  if (!deviceId.value) return
  const mode = await pickChangeMode()
  if (!mode) return
  ownSaving.value = true
  try {
    await http.post(`/asset/ownership-period/device/${deviceId.value}/change-location`, {
      location_floor: locForm.location_floor,
      room_number: locForm.room_number,
      location_detail: locForm.location_detail,
      mode
    })
    props.model.location_floor = locForm.location_floor
    props.model.room_number = locForm.room_number
    props.model.location_detail = locForm.location_detail
    ElMessage.success(mode === 'transfer' ? '已真变更位置' : '已纠错位置')
    locDialogVisible.value = false
  } finally {
    ownSaving.value = false
  }
}
</script>

<style scoped>
.device-ledger-form {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.device-ledger-form--side-nav {
  flex-direction: row;
  align-items: stretch;
  min-width: 0;
}

.device-ledger-form :deep(.form-tab-nav--top) {
  flex-shrink: 0;
}

.device-ledger-form--side-nav :deep(.form-tab-nav--side) {
  flex-shrink: 0;
  max-height: 100%;
}

.device-ledger-form__panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-gutter: stable;
  padding-right: 12px;
  padding-bottom: 8px;
}

.device-ledger-form__card-pane {
  padding-top: 4px;
}

.device-ledger-form__own-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.device-ledger-form__own-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
