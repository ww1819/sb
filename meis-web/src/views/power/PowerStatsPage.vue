<template>
  <div class="power-stats-page">
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6"><el-statistic title="监测基站" :value="Number(summary.stationCount ?? 0)" /></el-col>
      <el-col :span="6"><el-statistic title="监测标签" :value="Number(summary.tagCount ?? 0)" /></el-col>
      <el-col :span="6"><el-statistic title="今日用电(kWh)" :value="Number(summary.todayEnergyKwh ?? 0)" :precision="2" /></el-col>
      <el-col :span="6"><el-statistic title="告警设备" :value="Number(summary.alarmCount ?? 0)" /></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="运行状态分布">
          <el-table :data="stateCounts" size="small">
            <el-table-column prop="work_state" label="状态" />
            <el-table-column prop="count" label="数量" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="近30天运行TOP10">
          <el-table :data="ranking" size="small">
            <el-table-column prop="device_code" label="设备编码" />
            <el-table-column prop="device_name" label="设备名称" />
            <el-table-column prop="run_hours" label="运行时长(h)" />
            <el-table-column prop="energy_kwh" label="用电(kWh)" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <el-card header="近30天用电趋势" class="trend-card">
      <el-table :data="trend" size="small">
        <el-table-column prop="record_date" label="日期" />
        <el-table-column prop="run_hours" label="运行(h)" />
        <el-table-column prop="idle_hours" label="待机(h)" />
        <el-table-column prop="offline_hours" label="离线(h)" />
        <el-table-column prop="energy_kwh" label="用电(kWh)" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '@/api/http'

const summary = ref<Record<string, unknown>>({})
const ranking = ref<Record<string, unknown>[]>([])
const trend = ref<Record<string, unknown>[]>([])

const stateCounts = computed(() => (summary.value.stateCounts as Record<string, unknown>[]) ?? [])

async function load() {
  const [s, r, t] = await Promise.all([
    http.get('/power/stats/summary'),
    http.get('/power/stats/device-ranking'),
    http.get('/power/stats/daily-trend')
  ])
  summary.value = s.data.data ?? {}
  ranking.value = r.data.data ?? []
  trend.value = t.data.data ?? []
}

onMounted(load)
</script>

<style scoped>
.kpi-row { margin-bottom: 16px; }
.trend-card { margin-top: 16px; }
</style>
