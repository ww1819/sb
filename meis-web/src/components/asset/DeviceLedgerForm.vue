<template>
  <el-form label-width="96px" class="device-ledger-form" :disabled="isView">
    <FormTabNav v-model="activeTab" :tabs="visibleTabs" />

    <div class="device-ledger-form__panel">
      <GroupedFormFields
        v-show="activeTab === 'basic'"
        table="medical_device"
        :model="model"
        :fields="basicFields"
        :group-span="{ basic: 6, finance: 6 }"
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
import { computed, ref, watch } from 'vue'
import FormTabNav from '@/components/form/FormTabNav.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import DeviceAssetCard from '@/components/asset/DeviceAssetCard.vue'
import DeviceArchivePanel from '@/components/asset/tabs/DeviceArchivePanel.vue'
import DeviceImagePanel from '@/components/asset/tabs/DeviceImagePanel.vue'
import DeviceRecordTablePanel from '@/components/asset/tabs/DeviceRecordTablePanel.vue'
import DeviceLabelPanel from '@/components/asset/tabs/DeviceLabelPanel.vue'
import type { RecordColumn } from '@/components/asset/tabs/DeviceRecordTablePanel.vue'
import { getSchema, type FieldSchema } from '@/config/pageSchemas'

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
  { key: 'adverse', label: '不良事件' }
]

/** 编辑：基本信息+图片+档案；查看：全部（含标签与业务 Sheet） */
const visibleTabs = computed(() => {
  if (isView.value) return allTabs
  const editKeys = new Set(['basic', 'archive', 'images'])
  return allTabs.filter((t) => editKeys.has(t.key))
})

watch(
  () => props.mode,
  () => {
    activeTab.value = 'basic'
  }
)

const basicGroupKeys = new Set(['basic', 'finance', 'location', 'time', 'status', 'compliance', 'attachment', 'remark', 'other'])

const basicFields = computed(() => {
  const source = props.fields?.length ? props.fields : getSchema('medical_device')
  return source
    .filter((f) => basicGroupKeys.has(f.group ?? 'other'))
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
</style>
