<template>
  <div class="dashboard page-view--scroll">
    <el-row :gutter="12" class="kpi-row">
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

    <div
      class="dashboard-tabs-wrap"
      @mouseenter="onTabsHover(true)"
      @mouseleave="onTabsHover(false)"
    >
      <el-tabs v-model="activeTab" class="dashboard-tabs" @tab-change="onTabChange">
        <el-tab-pane name="workspace">
          <template #label>
            <span>日常办公</span>
            <el-badge v-if="todos.length" :value="todos.length" class="tab-badge" />
          </template>
          <el-row :gutter="16">
            <el-col :xs="24" :lg="profile.showTodos ? 14 : 24">
              <el-card shadow="never" class="panel-card">
                <template #header>
                  <div class="panel-header">快捷入口</div>
                </template>
                <QuickEntryGrid :items="quickEntries" @navigate="go" />
              </el-card>
            </el-col>
            <el-col v-if="profile.showTodos" :xs="24" :lg="10">
              <el-card shadow="never" class="panel-card panel-card--fill">
                <template #header>
                  <div class="panel-header">待办事项</div>
                </template>
                <FeedList :items="todos" type-field="todo_type" empty-text="暂无待办事项" :limit="12" />
              </el-card>
            </el-col>
          </el-row>
          <el-card shadow="never" class="panel-card progress-panel">
            <template #header>
              <div class="panel-header">状态概览</div>
            </template>
            <div class="progress-circle-row">
              <ProgressCircle
                v-for="item in progressCircleDemo"
                :key="item.variant"
                :variant="item.variant"
                :value="item.value"
                :radius="50"
              >
                <span class="progress-circle-label">{{ item.label }}</span>
              </ProgressCircle>
            </div>
          </el-card>
        </el-tab-pane>

        <el-tab-pane v-if="activeCharts.length" name="charts" lazy>
          <template #label>数据分析</template>
          <el-row :gutter="16" class="charts-row">
            <el-col
              v-for="chart in activeCharts"
              :key="chart.key"
              :xs="24"
              :lg="chart.span"
            >
              <ChartCard :title="chart.title" :option="chart.option" :height="chart.height" />
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane v-if="profile.showMessages" name="messages" lazy>
          <template #label>
            <span>消息中心</span>
            <el-badge v-if="unreadCount" :value="unreadCount" class="tab-badge" />
          </template>
          <el-card shadow="never" class="panel-card">
            <template #header>
              <div class="panel-header">消息通知</div>
            </template>
            <FeedList
              :items="messages"
              type-field="message_type"
              unread-field="is_read"
              empty-text="暂无消息"
              :limit="20"
            />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import type { TabPaneName } from 'element-plus'
import http from '@/api/http'
import { useTabsStore } from '@/stores/tabs'
import { useLayoutStore } from '@/stores/layout'
import { useDashboardProfile } from '@/composables/useDashboardProfile'
import { ALL_QUICK_ENTRIES, type DashboardChartKey } from '@/config/dashboardProfiles'
import StatCard from '@/components/dashboard/StatCard.vue'
import ChartCard from '@/components/dashboard/ChartCard.vue'
import QuickEntryGrid from '@/components/dashboard/QuickEntryGrid.vue'
import FeedList from '@/components/dashboard/FeedList.vue'
import ProgressCircle, { type ProgressCircleVariant } from '@/components/ProgressCircle.vue'
import {
  buildBarOption,
  buildLineOption,
  buildMultiKpiGaugeOption,
  buildPieOption,
  buildRosePieOption
} from '@/composables/useChartTheme'

const router = useRouter()
const tabs = useTabsStore()
const layoutStore = useLayoutStore()
const { profile } = useDashboardProfile()

const progressCircleDemo: { variant: ProgressCircleVariant; label: string; value: number }[] = [
  { variant: 'default', label: 'Default', value: 62 },
  { variant: 'neutral', label: 'Neutral', value: 62 },
  { variant: 'warning', label: 'Warning', value: 62 },
  { variant: 'success', label: 'Success', value: 62 },
  { variant: 'error', label: 'Error', value: 62 }
]

const stats = ref<Record<string, unknown>>({})
const todos = ref<Record<string, unknown>[]>([])
const messages = ref<Record<string, unknown>[]>([])
const activeTab = ref('workspace')
/** 日常办公 / 数据分析 / 消息中心自动轮播；鼠标悬停在 Tab 区域时暂停（DASH-UI-01） */
const TAB_ROTATE_MS = 5000
const tabHoverPaused = ref(false)
let tabRotateTimer: ReturnType<typeof setInterval> | null = null

const rotatableTabs = computed(() => {
  const names: string[] = ['workspace']
  if (profile.value.charts.length) names.push('charts')
  if (profile.value.showMessages) names.push('messages')
  return names
})

function stopTabRotate() {
  if (tabRotateTimer != null) {
    clearInterval(tabRotateTimer)
    tabRotateTimer = null
  }
}

function startTabRotate() {
  stopTabRotate()
  if (rotatableTabs.value.length < 2) return
  tabRotateTimer = setInterval(() => {
    if (tabHoverPaused.value) return
    const names = rotatableTabs.value
    if (names.length < 2) return
    const idx = names.indexOf(String(activeTab.value))
    const next = names[(idx < 0 ? 0 : idx + 1) % names.length]
    activeTab.value = next
    onTabChange(next)
  }, TAB_ROTATE_MS)
}

function onTabsHover(hovering: boolean) {
  tabHoverPaused.value = hovering
}

watch(rotatableTabs, (names) => {
  if (!names.includes(String(activeTab.value))) {
    activeTab.value = names[0] ?? 'workspace'
  }
  startTabRotate()
})

const kpiSpan = computed(() => (profile.value.kpis.length === 3 ? 8 : 6))

const quickEntries = computed(() => {
  const paths = new Set(profile.value.quickPaths)
  return ALL_QUICK_ENTRIES.filter((item) => paths.has(item.path))
})

const unreadCount = computed(() =>
  messages.value.filter((m) => !m.is_read).length
)

const emptyLine: EChartsOption = buildLineOption([], [])
const emptyBar: EChartsOption = buildBarOption([], [])
const emptyPie: EChartsOption = buildPieOption([])
const emptyRose: EChartsOption = buildRosePieOption([])

/** DASH-UI-04：示意多环 KPI（对齐 Highcharts Multiple KPI gauge） */
function buildKpiGaugeDemo(): EChartsOption {
  return buildMultiKpiGaugeOption(
    [
      { name: 'KPI A', value: 75, color: '#2CAFFE', trackColor: '#D6EEFF' },
      { name: 'KPI B', value: 60, color: '#544FC5', trackColor: '#E8E7F8' },
      { name: 'KPI C', value: 85, color: '#00E272', trackColor: '#D6FCE9' }
    ],
    'Conversion',
    '80%'
  )
}

function chartOptions() {
  const trend = (stats.value.repairTrend as { month: string; count: number }[]) ?? []
  const brands = (stats.value.brandTop10 as { brand: string; count: number }[]) ?? []
  const status = (stats.value.deviceStatus as { device_status: string; count: number }[]) ?? []
  const category =
    (stats.value.deviceCategory as { category_name: string; count: number }[]) ?? []
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
    category: !category.length
      ? emptyRose
      : buildRosePieOption(
          category.map((c) => ({ name: c.category_name, value: Number(c.count) })),
          '设备分类'
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
    kpiGauge: buildKpiGaugeDemo(),
    deptValue: !dept.length
      ? emptyBar
      : buildBarOption(
          dept.map((d) => d.dept_name),
          dept.map((d) => Number(d.total_value)),
          '资产价值'
        )
  } as Record<DashboardChartKey, EChartsOption>
}

const chartMeta: Record<DashboardChartKey, { title: string; height: string; wide?: boolean }> = {
  trend: { title: '维修趋势', height: '320px', wide: true },
  brand: { title: '品牌 TOP10', height: '320px', wide: true },
  status: { title: '设备状态', height: '300px' },
  category: { title: '设备分类', height: '300px' },
  origin: { title: '国产/进口', height: '300px' },
  newDevice: { title: '新增设备', height: '300px', wide: true },
  kpiGauge: { title: 'Multiple KPI gauge', height: '300px', wide: true },
  deptValue: { title: '科室资产价值', height: '320px', wide: true }
}

const activeCharts = computed(() => {
  void layoutStore.themeRevision
  const options = chartOptions()
  const keys = profile.value.charts
  const triplePies =
    keys.includes('status') && keys.includes('category') && keys.includes('origin')
  const newDevicePair = keys.includes('newDevice') && keys.includes('kpiGauge')
  return keys.map((key, index) => {
    const meta = chartMeta[key]
    const isWide = meta.wide ?? false
    const isLastOdd = keys.length % 2 === 1 && index === keys.length - 1
    const isTriplePie =
      triplePies && (key === 'status' || key === 'category' || key === 'origin')
    const isNewDevicePair =
      newDevicePair && (key === 'newDevice' || key === 'kpiGauge')
    return {
      key,
      title: meta.title,
      height: meta.height,
      option: options[key],
      span: isTriplePie ? 8 : isNewDevicePair ? 12 : isLastOdd ? 24 : isWide ? 12 : 12
    }
  })
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
  startTabRotate()
})

onUnmounted(() => {
  stopTabRotate()
})

function onTabChange(name: TabPaneName) {
  if (name === 'charts') {
    nextTick(() => window.dispatchEvent(new Event('resize')))
  }
}

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
.dashboard {
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.kpi-row {
  margin-bottom: 12px;
}

.dashboard-tabs-wrap {
  min-width: 0;
}

.dashboard-tabs {
  background: transparent;
}

.dashboard-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.dashboard-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.tab-badge {
  margin-left: 6px;
  vertical-align: middle;
}

.tab-badge :deep(.el-badge__content) {
  position: relative;
  top: 0;
  transform: none;
}

.charts-row {
  margin-bottom: 8px;
}

.charts-row .el-col {
  margin-bottom: 16px;
}

.panel-card {
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
  box-shadow: var(--meis-card-shadow);
}

.panel-card--fill {
  min-height: 240px;
}

.panel-card :deep(.el-card__header) {
  padding: 10px 16px;
  border-bottom: 1px solid var(--meis-border-light);
}

.panel-header {
  position: relative;
  padding-left: 10px;
  font-size: 14px;
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

.progress-panel {
  margin-top: 16px;
}

.progress-circle-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 32px;
  padding: 12px 8px 8px;
}

.progress-circle-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--meis-text-primary, #111827);
}
</style>
