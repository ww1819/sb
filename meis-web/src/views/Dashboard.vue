<template>
  <div class="dashboard">
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6"><el-statistic title="设备总数" :value="stats.deviceCount ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="待处理工单" :value="stats.openWorkorders ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="保养计划" :value="stats.activeMaintenancePlans ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="待审批" :value="stats.pendingApprovals ?? 0" /></el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="12">
        <el-card header="快捷入口">
          <el-space wrap>
            <el-button @click="go('/repair/workorder')">快速报修</el-button>
            <el-button @click="go('/asset/outbound')">设备领用</el-button>
            <el-button @click="go('/asset/inventory')">资产盘点</el-button>
            <el-button @click="go('/purchase/plan')">采购计划</el-button>
          </el-space>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="待办事项">
          <el-table :data="todos" size="small" max-height="200">
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="todo_type" label="类型" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="12"><el-card header="维修趋势"><div ref="trendRef" class="chart" /></el-card></el-col>
      <el-col :span="12"><el-card header="品牌 TOP10"><div ref="brandRef" class="chart" /></el-card></el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="8"><el-card header="设备状态"><div ref="statusRef" class="chart-sm" /></el-card></el-col>
      <el-col :span="8"><el-card header="国产/进口"><div ref="originRef" class="chart-sm" /></el-card></el-col>
      <el-col :span="8"><el-card header="新增设备"><div ref="newRef" class="chart-sm" /></el-card></el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="24">
        <el-card header="消息中心">
          <el-table :data="messages" size="small">
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="message_type" label="类型" width="100" />
            <el-table-column prop="created_at" label="时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import http from '@/api/http'
import { useTabsStore } from '@/stores/tabs'

const router = useRouter()
const tabs = useTabsStore()
const stats = ref<Record<string, unknown>>({})
const todos = ref<Record<string, unknown>[]>([])
const messages = ref<Record<string, unknown>[]>([])
const trendRef = ref<HTMLElement>()
const brandRef = ref<HTMLElement>()
const statusRef = ref<HTMLElement>()
const originRef = ref<HTMLElement>()
const newRef = ref<HTMLElement>()

onMounted(async () => {
  const [dash, todoRes, msgRes] = await Promise.all([
    http.get('/analytics/dashboard'),
    http.get('/analytics/dashboard/todos'),
    http.get('/notification/messages')
  ])
  stats.value = dash.data.data ?? {}
  todos.value = todoRes.data.data ?? []
  messages.value = msgRes.data.data ?? []
  renderCharts()
})

function renderCharts() {
  const trend = (stats.value.repairTrend as { month: string; count: number }[]) ?? []
  if (trendRef.value) {
    echarts.init(trendRef.value).setOption({
      xAxis: { type: 'category', data: trend.map((t) => t.month) },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: trend.map((t) => t.count) }]
    })
  }
  const brands = (stats.value.brandTop10 as { brand: string; count: number }[]) ?? []
  if (brandRef.value) {
    echarts.init(brandRef.value).setOption({
      xAxis: { type: 'category', data: brands.map((b) => b.brand) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: brands.map((b) => b.count) }]
    })
  }
  const status = (stats.value.deviceStatus as { device_status: string; count: number }[]) ?? []
  if (statusRef.value) {
    echarts.init(statusRef.value).setOption({
      series: [{ type: 'pie', data: status.map((s) => ({ name: s.device_status, value: s.count })) }]
    })
  }
  const origin = (stats.value.importDomestic as { country: string; count: number }[]) ?? []
  if (originRef.value) {
    echarts.init(originRef.value).setOption({
      series: [{ type: 'pie', data: origin.map((o) => ({ name: o.country, value: o.count })) }]
    })
  }
  const nd = (stats.value.newDevices as { month: string; count: number }[]) ?? []
  if (newRef.value) {
    echarts.init(newRef.value).setOption({
      xAxis: { type: 'category', data: nd.map((n) => n.month) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: nd.map((n) => n.count) }]
    })
  }
}

function go(path: string) {
  tabs.open(path, path)
  router.push(path)
}
</script>

<style scoped>
.kpi-row { margin-bottom: 16px; }
.section { margin-bottom: 16px; }
.chart { height: 260px; }
.chart-sm { height: 220px; }
</style>
