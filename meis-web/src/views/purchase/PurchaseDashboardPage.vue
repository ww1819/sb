<template>
  <div class="purchase-dashboard page-view--scroll">
    <div class="dash-toolbar">
      <span class="dash-title">采购执行看板</span>
      <div class="dash-actions">
        <el-button size="small" :loading="alertLoading" @click="refreshAlerts">刷新预警</el-button>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="item in kpis" :key="item.key" :span="6">
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-title">{{ item.label }}</div>
          <div class="kpi-value" :class="item.warn ? 'warn' : item.highlight ? 'highlight' : ''">
            {{ formatVal(stats[item.key], item.suffix) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="8">
        <ChartCard title="采购漏斗" :option="funnelOption" height="280px" />
      </el-col>
      <el-col :span="8">
        <ChartCard title="计划审批分布" :option="planChartOption" height="280px" />
      </el-col>
      <el-col :span="8">
        <ChartCard title="项目状态分布" :option="projectChartOption" height="280px" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="14">
        <ChartCard title="计划预算 vs 合同金额（TOP8）" :option="budgetChartOption" height="300px" />
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" class="alert-card">
          <template #header>
            <div class="alert-header">
              <span>预警信息</span>
              <el-tag v-if="alerts.length" type="danger" size="small">{{ alerts.length }}</el-tag>
            </div>
          </template>
          <el-empty v-if="!alerts.length" description="暂无预警" />
          <el-alert
            v-for="(a, i) in alerts"
            :key="i"
            :title="a.title"
            :description="a.message"
            :type="a.level === 'danger' ? 'error' : 'warning'"
            show-icon
            class="alert-item"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="section">
      <template #header>全流程追溯（计划→项目→合同→验收→入库）</template>
      <el-table :data="pipeline" size="small" max-height="360" stripe>
        <el-table-column prop="business_chain_no" label="业务链" min-width="100" />
        <el-table-column prop="plan_code" label="计划" min-width="90" />
        <el-table-column prop="project_code" label="项目" min-width="90" />
        <el-table-column prop="contract_code" label="合同" min-width="90" />
        <el-table-column prop="acceptance_no" label="验收" min-width="90" />
        <el-table-column prop="entry_no" label="入库" min-width="90" />
        <el-table-column prop="payment_progress" label="付款%" width="72" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import http from '@/api/http'
import ChartCard from '@/components/dashboard/ChartCard.vue'

const loading = ref(false)
const alertLoading = ref(false)
const stats = ref<Record<string, number>>({})
const pipeline = ref<Record<string, unknown>[]>([])
const alerts = ref<{ title: string; message: string; level: string }[]>([])
const funnel = ref<{ name: string; value: number }[]>([])
const planStatusChart = ref<{ name: string; value: number }[]>([])
const projectStatusChart = ref<{ name: string; value: number }[]>([])
const budgetTopPlans = ref<{ name: string; budget: number; contracted: number }[]>([])

const kpis = [
  { key: 'executionRate', label: '预算执行率%', suffix: '%', highlight: true },
  { key: 'planConversionRate', label: '计划批转率%', suffix: '%' },
  { key: 'planApproved', label: '已批计划' },
  { key: 'projectAwarded', label: '已定标项目' },
  { key: 'contractActive', label: '生效合同' },
  { key: 'acceptancePending', label: '待验收' },
  { key: 'overBudgetCount', label: '超预算项目', warn: true },
  { key: 'overdueContractCount', label: '交货超期', warn: true }
]

function formatVal(v: unknown, suffix?: string) {
  if (v == null) return suffix === '%' ? '0%' : '0'
  return suffix ? `${v}${suffix}` : String(v)
}

const funnelOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'funnel',
    left: '10%',
    width: '80%',
    data: funnel.value.map(f => ({ name: f.name, value: f.value }))
  }]
}))

const planChartOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie',
    radius: ['40%', '68%'],
    data: planStatusChart.value.map(d => ({ name: d.name, value: d.value }))
  }]
}))

const projectChartOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie',
    radius: '65%',
    data: projectStatusChart.value.map(d => ({ name: d.name, value: d.value }))
  }]
}))

const budgetChartOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['预算', '合同'] },
  xAxis: { type: 'category', data: budgetTopPlans.value.map(b => b.name), axisLabel: { rotate: 30 } },
  yAxis: { type: 'value' },
  series: [
    { name: '预算', type: 'bar', data: budgetTopPlans.value.map(b => b.budget) },
    { name: '合同', type: 'bar', data: budgetTopPlans.value.map(b => b.contracted) }
  ]
}))

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/purchase/dashboard/stats')
    if (data.code === 0 && data.data) {
      stats.value = data.data
      pipeline.value = data.data.pipeline ?? []
      alerts.value = data.data.alerts ?? []
      funnel.value = data.data.funnel ?? []
      planStatusChart.value = data.data.planStatusChart ?? []
      projectStatusChart.value = data.data.projectStatusChart ?? []
      budgetTopPlans.value = data.data.budgetTopPlans ?? []
    }
  } finally {
    loading.value = false
  }
}

async function refreshAlerts() {
  alertLoading.value = true
  try {
    const { data } = await http.post('/purchase/dashboard/refresh-alerts')
    if (data.code === 0) {
      alerts.value = data.data ?? []
      ElMessage.success(`已刷新预警，当前 ${alerts.value.length} 条`)
    }
  } finally {
    alertLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.purchase-dashboard { padding: 8px; }
.dash-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.dash-actions { display: flex; gap: 8px; }
.dash-title { font-size: 16px; font-weight: 600; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { min-height: 88px; }
.kpi-title { color: var(--el-text-color-secondary); font-size: 13px; }
.kpi-value { font-size: 26px; font-weight: 600; margin-top: 8px; }
.kpi-value.warn { color: var(--el-color-danger); }
.kpi-value.highlight { color: var(--el-color-primary); }
.section { margin-bottom: 16px; }
.alert-card { height: 100%; }
.alert-header { display: flex; align-items: center; gap: 8px; }
.alert-item { margin-bottom: 8px; }
</style>
