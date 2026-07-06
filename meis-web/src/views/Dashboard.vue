<template>
  <div class="dashboard dashboard-stagger">
    <div class="dashboard-hero">
      <div>
        <h2 class="hero-title">{{ profile.title }}</h2>
        <p class="hero-sub">{{ profile.subtitle }}</p>
      </div>
      <el-tag type="info" effect="plain" round>{{ roleLabel }}</el-tag>
    </div>

    <el-row :gutter="16" class="kpi-row">
      <el-col v-for="item in profile.kpis" :key="item.key" :span="kpiSpan">
        <StatCard
          :title="item.title"
          :value="numVal(stats[item.key])"
          :icon="item.icon"
          :color="item.color"
          :bg-color="item.bgColor"
          :hint="item.hint"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section">
      <el-col :span="profile.showTodos ? 12 : 24">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">快捷入口</div>
          </template>
          <QuickEntryGrid :items="quickEntries" @navigate="go" />
        </el-card>
      </el-col>
      <el-col v-if="profile.showTodos" :span="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">待办事项</div>
          </template>
          <FeedList :items="todos" type-field="todo_type" empty-text="暂无待办事项" max-height="220px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row v-if="chartRow1.length" :gutter="16" class="section">
      <el-col v-for="chart in chartRow1" :key="chart.key" :span="chart.span">
        <ChartCard :title="chart.title" :option="chart.option" :height="chart.height" />
      </el-col>
    </el-row>

    <el-row v-if="chartRow2.length" :gutter="16" class="section">
      <el-col v-for="chart in chartRow2" :key="chart.key" :span="chart.span">
        <ChartCard :title="chart.title" :option="chart.option" :height="chart.height" />
      </el-col>
    </el-row>

    <el-row v-if="profile.showMessages" :gutter="16" class="section">
      <el-col :span="24">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">消息中心</div>
          </template>
          <FeedList
            :items="messages"
            type-field="message_type"
            unread-field="is_read"
            empty-text="暂无消息"
            max-height="280px"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import http from '@/api/http'
import { useTabsStore } from '@/stores/tabs'
import { useLayoutStore } from '@/stores/layout'
import { useDashboardProfile } from '@/composables/useDashboardProfile'
import { ALL_QUICK_ENTRIES, type DashboardChartKey } from '@/config/dashboardProfiles'
import StatCard from '@/components/dashboard/StatCard.vue'
import ChartCard from '@/components/dashboard/ChartCard.vue'
import QuickEntryGrid from '@/components/dashboard/QuickEntryGrid.vue'
import FeedList from '@/components/dashboard/FeedList.vue'
import { buildBarOption, buildLineOption, buildPieOption } from '@/composables/useChartTheme'

const router = useRouter()
const tabs = useTabsStore()
const layoutStore = useLayoutStore()
const { profile, profileId } = useDashboardProfile()

const stats = ref<Record<string, unknown>>({})
const todos = ref<Record<string, unknown>[]>([])
const messages = ref<Record<string, unknown>[]>([])

const kpiSpan = computed(() => (profile.value.kpis.length === 3 ? 8 : 6))

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    admin: '管理员视图',
    asset: '资产管理视图',
    repair: '维修工程师视图',
    purchase: '采购管理视图'
  }
  return map[profileId.value] ?? '工作台'
})

const quickEntries = computed(() => {
  const paths = new Set(profile.value.quickPaths)
  return ALL_QUICK_ENTRIES.filter((item) => paths.has(item.path)).slice(0, 4)
})

const emptyLine: EChartsOption = buildLineOption([], [])
const emptyBar: EChartsOption = buildBarOption([], [])
const emptyPie: EChartsOption = buildPieOption([])

function chartOptions() {
  const trend = (stats.value.repairTrend as { month: string; count: number }[]) ?? []
  const brands = (stats.value.brandTop10 as { brand: string; count: number }[]) ?? []
  const status = (stats.value.deviceStatus as { device_status: string; count: number }[]) ?? []
  const origin = (stats.value.importDomestic as { country: string; count: number }[]) ?? []
  const nd = (stats.value.newDevices as { month: string; count: number }[]) ?? []
  const dept = (stats.value.deptValue as { dept_name: string; total_value: number }[]) ?? []

  return {
    trend: !trend.length
      ? emptyLine
      : buildLineOption(
          trend.map((t) => t.month),
          trend.map((t) => t.count),
          '维修量'
        ),
    brand: !brands.length
      ? emptyBar
      : buildBarOption(
          brands.map((b) => b.brand),
          brands.map((b) => b.count),
          '设备数'
        ),
    status: !status.length
      ? emptyPie
      : buildPieOption(
          status.map((s) => ({ name: s.device_status, value: s.count })),
          '设备状态'
        ),
    origin: !origin.length
      ? emptyPie
      : buildPieOption(
          origin.map((o) => ({ name: o.country, value: o.count })),
          '来源'
        ),
    newDevice: !nd.length
      ? emptyBar
      : buildBarOption(
          nd.map((n) => n.month),
          nd.map((n) => n.count),
          '新增'
        ),
    deptValue: !dept.length
      ? emptyBar
      : buildBarOption(
          dept.map((d) => d.dept_name),
          dept.map((d) => Number(d.total_value)),
          '资产价值'
        )
  } as Record<DashboardChartKey, EChartsOption>
}

const chartMeta: Record<DashboardChartKey, { title: string; height?: string }> = {
  trend: { title: '维修趋势' },
  brand: { title: '品牌 TOP10' },
  status: { title: '设备状态', height: '220px' },
  origin: { title: '国产/进口', height: '220px' },
  newDevice: { title: '新增设备', height: '220px' },
  deptValue: { title: '科室资产价值', height: '220px' }
}

const activeCharts = computed(() => {
  void layoutStore.themeRevision
  const options = chartOptions()
  return profile.value.charts.map((key) => ({
    key,
    title: chartMeta[key].title,
    height: chartMeta[key].height,
    option: options[key]
  }))
})

const chartRow1 = computed(() => {
  const list = activeCharts.value
  if (list.length <= 2) return list.map((c) => ({ ...c, span: list.length === 1 ? 24 : 12 }))
  return list.slice(0, 2).map((c) => ({ ...c, span: 12 }))
})

const chartRow2 = computed(() => {
  const list = activeCharts.value
  if (list.length <= 2) return []
  return list.slice(2).map((c) => ({ ...c, span: list.slice(2).length === 1 ? 24 : 8 }))
})

watch(() => layoutStore.themeRevision, () => {
  // trigger chart option rebuild via activeCharts dependency
})

onMounted(async () => {
  const [dash, todoRes, msgRes] = await Promise.all([
    http.get('/analytics/dashboard'),
    http.get('/analytics/dashboard/todos'),
    http.get('/notification/messages')
  ])
  stats.value = dash.data.data ?? {}
  todos.value = todoRes.data.data ?? []
  messages.value = msgRes.data.data ?? []
})

function go(path: string) {
  tabs.open(path, path)
  router.push(path)
}

function numVal(v: unknown) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
</script>

<style scoped>
.dashboard-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 18px 20px;
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, #fff 100%);
  box-shadow: var(--meis-card-shadow);
}

html.dark .dashboard-hero {
  background: linear-gradient(135deg, rgba(22, 119, 255, 0.12) 0%, #1d1d1d 100%);
}

.hero-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.hero-sub {
  margin: 6px 0 0;
  font-size: var(--meis-font-subtitle);
  color: var(--meis-text-secondary);
}

.kpi-row {
  margin-bottom: 16px;
}

.section {
  margin-bottom: 16px;
}

.panel-card {
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
  box-shadow: var(--meis-card-shadow);
}

.panel-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid var(--meis-border-light);
}

.panel-header {
  position: relative;
  padding-left: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.panel-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  background: var(--el-color-primary);
}
</style>
