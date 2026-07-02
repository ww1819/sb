<template>
  <el-tabs v-model="tab">
    <el-tab-pane label="资产报表" name="asset">
      <el-table :data="deviceStatus" border><el-table-column prop="device_status" label="状态" /><el-table-column prop="count" label="数量" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="维修报表" name="repair">
      <el-table :data="repairVolume" border><el-table-column prop="dept_name" label="科室" /><el-table-column prop="count" label="报修量" /></el-table>
      <el-table :data="faultType" border class="mt"><el-table-column prop="fault_name" label="故障类型" /><el-table-column prop="count" label="次数" /></el-table>
      <el-table :data="repairCost" border class="mt"><el-table-column prop="month" label="月份" /><el-table-column prop="cost" label="费用" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="效益报表" name="benefit">
      <el-table :data="benefit" border><el-table-column prop="summary_year" label="年" /><el-table-column prop="summary_month" label="月" /><el-table-column prop="profit" label="利润" /></el-table>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const tab = ref('asset')
const deviceStatus = ref<Record<string, unknown>[]>([])
const repairVolume = ref<Record<string, unknown>[]>([])
const faultType = ref<Record<string, unknown>[]>([])
const repairCost = ref<Record<string, unknown>[]>([])
const benefit = ref<Record<string, unknown>[]>([])

onMounted(async () => {
  const [a, rv, ft, rc, b] = await Promise.all([
    http.get('/analytics/reports/device-status'),
    http.get('/analytics/reports/repair-volume'),
    http.get('/analytics/reports/fault-type'),
    http.get('/analytics/reports/repair-cost'),
    http.get('/analytics/reports/benefit-summary')
  ])
  deviceStatus.value = a.data.data ?? []
  repairVolume.value = rv.data.data ?? []
  faultType.value = ft.data.data ?? []
  repairCost.value = rc.data.data ?? []
  benefit.value = b.data.data ?? []
})
</script>

<style scoped>
.mt { margin-top: 12px; }
</style>
