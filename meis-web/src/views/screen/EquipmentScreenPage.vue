<template>
  <div class="equipment-screen">
    <div class="screen-header">
      <div class="screen-title">
        <span class="title-main">医疗设备运营监控大屏</span>
        <span class="title-sub">{{ nowText }}</span>
      </div>
      <div class="screen-actions">
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" type="primary" @click="exitFullscreen">退出全屏</el-button>
      </div>
    </div>

    <el-row :gutter="12" class="kpi-row">
      <el-col v-for="item in kpiItems" :key="item.key" :span="3">
        <div class="kpi-card" :class="item.warn ? 'kpi-card--warn' : ''">
          <div class="kpi-label">{{ item.label }}</div>
          <div class="kpi-value">{{ formatKpi(item.key) }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="chart-row">
      <el-col :span="8">
        <div class="panel">
          <div class="panel-title">设备状态分布</div>
          <ChartCard title="" :option="deviceStatusOption" height="220px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-title">科室设备分布</div>
          <ChartCard title="" :option="deptOption" height="220px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <div class="panel-title">维修量趋势（12月）</div>
          <ChartCard title="" :option="repairTrendOption" height="220px" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="bottom-row">
      <el-col :span="10">
        <div class="panel panel--table">
          <div class="panel-title">维修动态</div>
          <el-table :data="repairDynamic" size="small" class="screen-table" max-height="240">
            <el-table-column prop="wo_no" label="工单号" width="110" />
            <el-table-column prop="device_name" label="设备" min-width="120" show-overflow-tooltip />
            <el-table-column prop="dept_name" label="科室" width="90" />
            <el-table-column prop="status" label="状态" width="80" />
            <el-table-column prop="created_at" label="时间" min-width="130" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="7">
        <div class="panel panel--table">
          <div class="panel-title">质控到期提醒（7天内）</div>
          <el-table :data="qcDueList" size="small" class="screen-table" max-height="240">
            <el-table-column prop="qc_type" label="类型" width="60" />
            <el-table-column prop="device_name" label="设备" min-width="100" show-overflow-tooltip />
            <el-table-column prop="due_date" label="到期日" width="100" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="7">
        <div class="panel panel--table">
          <div class="panel-title">效益 TOP / 采购简报</div>
          <div class="mini-stats">
            <span>待审计划 {{ purchaseBrief.pendingPlans ?? 0 }}</span>
            <span>生效合同 {{ purchaseBrief.activeContracts ?? 0 }}</span>
            <span>待验收 {{ purchaseBrief.pendingAcceptance ?? 0 }}</span>
            <span>本月入库 {{ purchaseBrief.monthEntries ?? 0 }}</span>
          </div>
          <el-table :data="benefitTop" size="small" class="screen-table" max-height="190">
            <el-table-column prop="device_name" label="设备" min-width="100" show-overflow-tooltip />
            <el-table-column prop="net_profit" label="净利润" width="80" />
            <el-table-column prop="benefit_level" label="等级" width="70" />
          </el-table>
          <div class="power-bar">
            电流监测：运行 {{ powerOverview.runningCount ?? 0 }} 台 · 今日用电 {{ Number(powerOverview.todayEnergyKwh ?? 0).toFixed(1) }} kWh
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import http from '@/api/http'
import ChartCard from '@/components/dashboard/ChartCard.vue'
import { useLayoutStore } from '@/stores/layout'

const layoutStore = useLayoutStore()
const loading = ref(false)
const kpis = ref<Record<string, unknown>>({})
const deviceStatus = ref<Record<string, unknown>[]>([])
const deptDistribution = ref<Record<string, unknown>[]>([])
const repairTrend = ref<Record<string, unknown>[]>([])
const repairDynamic = ref<Record<string, unknown>[]>([])
const qcDueList = ref<Record<string, unknown>[]>([])
const benefitTop = ref<Record<string, unknown>[]>([])
const powerOverview = ref<Record<string, unknown>>({})
const purchaseBrief = ref<Record<string, unknown>>({})
const nowText = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const kpiItems = [
  { key: 'deviceCount', label: '在用设备' },
  { key: 'openRepairs', label: '在修工单', warn: true },
  { key: 'activeMaintenance', label: '保养计划' },
  { key: 'dueQcCount', label: '7日到期', warn: true },
  { key: 'alarmDevices', label: '电流告警', warn: true },
  { key: 'pendingApprovals', label: '待审批' },
  { key: 'lifeSupportCount', label: '生命支持' },
  { key: 'totalAssetValue', label: '资产总值(万)' }
]

const darkPie = { backgroundColor: 'transparent', textStyle: { color: '#9ecbff' } }

const deviceStatusOption = computed<EChartsOption>(() => ({
  ...darkPie,
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie', radius: ['42%', '68%'],
    data: deviceStatus.value.map(r => ({ name: r.name, value: r.value })),
    label: { color: '#cfe6ff' }
  }]
}))

const deptOption = computed<EChartsOption>(() => ({
  ...darkPie,
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 12, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: deptDistribution.value.map(r => r.name), axisLabel: { color: '#9ecbff', rotate: 30 } },
  yAxis: { type: 'value', axisLabel: { color: '#9ecbff' }, splitLine: { lineStyle: { color: 'rgba(80,140,220,0.15)' } } },
  series: [{ type: 'bar', data: deptDistribution.value.map(r => r.value), itemStyle: { color: '#3aa0ff' } }]
}))

const repairTrendOption = computed<EChartsOption>(() => ({
  ...darkPie,
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 12, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: repairTrend.value.map(r => r.month), axisLabel: { color: '#9ecbff' } },
  yAxis: { type: 'value', axisLabel: { color: '#9ecbff' }, splitLine: { lineStyle: { color: 'rgba(80,140,220,0.15)' } } },
  series: [{ type: 'line', smooth: true, data: repairTrend.value.map(r => r.count), areaStyle: { opacity: 0.15 }, itemStyle: { color: '#5ad8a6' } }]
}))

function formatKpi(key: string) {
  const v = kpis.value[key]
  if (key === 'totalAssetValue') return (Number(v ?? 0) / 10000).toFixed(1)
  return v ?? 0
}

function tickClock() {
  nowText.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/screen/equipment/dashboard')
    const d = data.data ?? {}
    kpis.value = (d.kpis as Record<string, unknown>) ?? {}
    deviceStatus.value = (d.deviceStatus as Record<string, unknown>[]) ?? []
    deptDistribution.value = (d.deptDistribution as Record<string, unknown>[]) ?? []
    repairTrend.value = (d.repairTrend as Record<string, unknown>[]) ?? []
    repairDynamic.value = (d.repairDynamic as Record<string, unknown>[]) ?? []
    qcDueList.value = (d.qcDueList as Record<string, unknown>[]) ?? []
    benefitTop.value = (d.benefitTop as Record<string, unknown>[]) ?? []
    powerOverview.value = (d.powerOverview as Record<string, unknown>) ?? {}
    purchaseBrief.value = (d.purchaseBrief as Record<string, unknown>) ?? {}
  } finally {
    loading.value = false
  }
}

function exitFullscreen() {
  layoutStore.setContentFullscreen(false)
}

onMounted(() => {
  layoutStore.setContentFullscreen(true)
  tickClock()
  load()
  timer = setInterval(() => { tickClock(); load() }, 60000)
})

onUnmounted(() => {
  layoutStore.setContentFullscreen(false)
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.equipment-screen {
  min-height: calc(100vh - 48px);
  padding: 12px 16px 20px;
  background: linear-gradient(180deg, #061428 0%, #0a1f3d 45%, #071526 100%);
  color: #d8ecff;
}

.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.title-main {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #7ec8ff, #d8ecff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.title-sub {
  margin-left: 16px;
  font-size: 13px;
  color: #7aa8d8;
}

.kpi-row { margin-bottom: 12px; }

.kpi-card {
  padding: 12px 10px;
  border-radius: 8px;
  border: 1px solid rgba(70, 130, 210, 0.35);
  background: rgba(12, 40, 78, 0.65);
  text-align: center;
}

.kpi-card--warn .kpi-value { color: #ff9f7f; }

.kpi-label { font-size: 12px; color: #8eb9e8; margin-bottom: 6px; }
.kpi-value { font-size: 22px; font-weight: 700; color: #5ddefb; }

.panel {
  border: 1px solid rgba(70, 130, 210, 0.3);
  border-radius: 8px;
  background: rgba(8, 28, 56, 0.72);
  padding: 8px 10px 4px;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #a8d4ff;
  margin-bottom: 4px;
  padding-left: 8px;
  border-left: 3px solid #3aa0ff;
}

.panel :deep(.chart-card) {
  background: transparent;
  border: none;
  box-shadow: none;
}

.panel :deep(.el-card__header) { display: none; }
.panel :deep(.el-card__body) { padding: 0; }

.screen-table :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(20, 55, 95, 0.8);
  --el-table-row-hover-bg-color: rgba(30, 70, 120, 0.45);
  --el-table-text-color: #cfe6ff;
  --el-table-header-text-color: #9ecbff;
  background: transparent;
}

.mini-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
  color: #8eb9e8;
  margin-bottom: 8px;
}

.power-bar {
  margin-top: 8px;
  font-size: 12px;
  color: #7ec8ff;
}
</style>
