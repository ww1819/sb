<template>
  <div class="special-alert-page">
    <el-row :gutter="12" class="summary-row">
      <el-col :span="6"><el-statistic title="提醒总数" :value="summary.total_count ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="证照到期" :value="(summary.license_expiring ?? []).length" /></el-col>
      <el-col :span="6"><el-statistic title="租赁到期" :value="(summary.lease_expiring ?? []).length" /></el-col>
      <el-col :span="6"><el-statistic title="测试/检验到期" :value="testInspectionCount" /></el-col>
    </el-row>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="证照到期" name="license">
        <AlertTable :rows="summary.license_expiring ?? []" :columns="licenseCols" />
      </el-tab-pane>
      <el-tab-pane label="租赁到期" name="lease">
        <AlertTable :rows="summary.lease_expiring ?? []" :columns="leaseCols" />
      </el-tab-pane>
      <el-tab-pane label="生命支持测试" name="test">
        <AlertTable :rows="summary.test_due ?? []" :columns="testCols" />
      </el-tab-pane>
      <el-tab-pane label="特种设备检验" name="inspection">
        <AlertTable :rows="summary.inspection_due ?? []" :columns="inspectionCols" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '@/api/http'
import AlertTable from './SpecialAlertTable.vue'

type Summary = {
  total_count?: number
  license_expiring?: Record<string, unknown>[]
  lease_expiring?: Record<string, unknown>[]
  test_due?: Record<string, unknown>[]
  inspection_due?: Record<string, unknown>[]
}

const summary = ref<Summary>({})
const activeTab = ref('license')

const testInspectionCount = computed(
  () => (summary.value.test_due?.length ?? 0) + (summary.value.inspection_due?.length ?? 0)
)

const licenseCols = [
  { prop: 'device_code', label: '设备编码' },
  { prop: 'device_name', label: '设备名称' },
  { prop: 'special_type', label: '类型', dict: 'special_type' },
  { prop: 'license_no', label: '许可证号' },
  { prop: 'license_expiry_date', label: '到期日' }
]

const leaseCols = [
  { prop: 'device_code', label: '设备编码' },
  { prop: 'device_name', label: '设备名称' },
  { prop: 'lease_end_date', label: '到期日' },
  { prop: 'monthly_rent', label: '月租金' },
  { prop: 'status', label: '状态', dict: 'lease_status' }
]

const testCols = [
  { prop: 'device_code', label: '设备编码' },
  { prop: 'device_name', label: '设备名称' },
  { prop: 'next_test_date', label: '待测日期' },
  { prop: 'standby_status', label: '状态', dict: 'standby_status' }
]

const inspectionCols = [
  { prop: 'device_code', label: '设备编码' },
  { prop: 'device_name', label: '设备名称' },
  { prop: 'special_type', label: '类型', dict: 'special_type' },
  { prop: 'next_inspection_date', label: '待检日期' }
]

async function loadSummary() {
  const { data } = await http.get('/special/alerts/summary')
  summary.value = data.data ?? {}
}

onMounted(loadSummary)
</script>

<style scoped>
.summary-row {
  margin-bottom: 12px;
}
.special-alert-page {
  height: 100%;
  overflow: auto;
}
</style>
