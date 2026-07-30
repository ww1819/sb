<template>
  <div class="dashboard page-view--scroll">
    <el-row :gutter="12" class="kpi-row">
      <el-col v-for="item in profile.kpis" :key="item.key" :span="kpiSpan">
        <BorderBeam :color="kpiBorderBeamColor" :duration="2.5" :beam-percent="20" :border-width="2.5">
          <StatCard
            :title="item.title"
            :value="numVal(stats[item.key])"
            :icon="item.icon"
            :color="item.color"
            :bg-color="item.bgColor"
            :hint="item.hint"
          />
        </BorderBeam>
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
          <el-row :gutter="16" class="workspace-panels">
            <el-col :xs="24" :lg="profile.showTodos ? 14 : 24">
              <el-card shadow="never" class="panel-card">
                <template #header>
                  <div class="panel-header panel-header--with-action">
                    <span>快捷入口</span>
                    <el-button
                      class="panel-header-action"
                      text
                      type="primary"
                      :icon="Setting"
                      title="设置快捷入口"
                      @click="settingsOpen = true"
                    />
                  </div>
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
            <template v-for="chart in activeCharts" :key="chart.key">
              <el-col :xs="24" :lg="chart.span">
                <ChartCard :title="chart.title" :option="chart.option" :height="chart.height" />
              </el-col>
              <el-col v-if="chart.key === assetDistAfterKey" :xs="24" :lg="assetDistSpan">
                <AssetDistributionChart :items="deviceByDept" />
              </el-col>
            </template>
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

    <QuickEntrySettingsDialog
      v-model="settingsOpen"
      :modules="navModules"
      :selected-paths="displayQuickPaths"
      @saved="onQuickEntrySaved"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import type { TabPaneName } from 'element-plus'
import { Menu, Setting } from '@element-plus/icons-vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useTabsStore } from '@/stores/tabs'
import { useLayoutStore } from '@/stores/layout'
import { useDashboardProfile } from '@/composables/useDashboardProfile'
import { ALL_QUICK_ENTRIES, type DashboardChartKey } from '@/config/dashboardProfiles'
import StatCard from '@/components/dashboard/StatCard.vue'
import BorderBeam, { BORDER_BEAM_OCEAN } from '@/components/dashboard/BorderBeam.vue'
import ChartCard from '@/components/dashboard/ChartCard.vue'
import QuickEntryGrid, { type QuickEntryItem } from '@/components/dashboard/QuickEntryGrid.vue'
import QuickEntrySettingsDialog from '@/components/dashboard/QuickEntrySettingsDialog.vue'
import FeedList from '@/components/dashboard/FeedList.vue'
import ProgressCircle, { type ProgressCircleVariant } from '@/components/ProgressCircle.vue'
import AssetDistributionChart from '@/components/dashboard/AssetDistributionChart.vue'
import {
  flattenMenus,
  normalizeNavModules,
  type NavModule
} from '@/utils/menuNav'
import {
  buildBarOption,
  buildLineOption,
  buildMultiKpiGaugeOption,
  buildPieOption,
  buildRosePieOption,
  CHART_COLORS,
  type MultiKpiRing
} from '@/composables/useChartTheme'
import { useDict } from '@/composables/useDict'

const router = useRouter()
const auth = useAuthStore()
const tabs = useTabsStore()
const layoutStore = useLayoutStore()
const { profile } = useDashboardProfile()
const { loadDict, resolveDictLabel, cacheVersion } = useDict()

const QUICK_ENTRY_COLORS = [
  { color: '#1677ff', bgColor: 'rgba(22, 119, 255, 0.08)' },
  { color: '#13c2c2', bgColor: 'rgba(19, 194, 194, 0.08)' },
  { color: '#722ed1', bgColor: 'rgba(114, 46, 209, 0.08)' },
  { color: '#fa8c16', bgColor: 'rgba(250, 140, 22, 0.08)' },
  { color: '#52c41a', bgColor: 'rgba(82, 196, 26, 0.08)' },
  { color: '#fa541c', bgColor: 'rgba(250, 84, 28, 0.08)' }
]

/** 工作台 KPI 边框流光：antd BorderBeam Ocean 预设 */
const kpiBorderBeamColor = BORDER_BEAM_OCEAN

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
const settingsOpen = ref(false)
const navModules = ref<NavModule[]>([])
/** null = 未配置，走角色默认；数组 = 已保存（可为空） */
const savedQuickPaths = ref<string[] | null>(null)

const deviceByDept = computed(() => {
  const raw = stats.value.deviceByDept
  if (!Array.isArray(raw)) return []
  return raw.map((row) => {
    const r = row as Record<string, unknown>
    return {
      dept_name: String(r.dept_name ?? '未分配'),
      count: Number(r.count) || 0
    }
  })
})

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

const allowedMenuByPath = computed(() => {
  const map = new Map<string, { title: string; moduleTitle: string }>()
  for (const item of flattenMenus(navModules.value)) {
    if (!item.path) continue
    map.set(item.path, { title: item.title, moduleTitle: item.moduleTitle })
  }
  return map
})

const displayQuickPaths = computed(() => {
  const allowed = allowedMenuByPath.value
  const source =
    savedQuickPaths.value != null ? savedQuickPaths.value : profile.value.quickPaths
  return source.filter((p) => allowed.has(p))
})

const presetByPath = new Map(ALL_QUICK_ENTRIES.map((e) => [e.path, e]))

function colorForPath(path: string) {
  let hash = 0
  for (let i = 0; i < path.length; i++) hash = (hash * 31 + path.charCodeAt(i)) | 0
  return QUICK_ENTRY_COLORS[Math.abs(hash) % QUICK_ENTRY_COLORS.length]
}

const quickEntries = computed<QuickEntryItem[]>(() => {
  return displayQuickPaths.value.map((path) => {
    const preset = presetByPath.get(path)
    const menu = allowedMenuByPath.value.get(path)
    const palette = colorForPath(path)
    return {
      label: menu?.title ?? preset?.label ?? path,
      desc: preset?.desc,
      path,
      icon: (preset?.icon ?? Menu) as Component,
      color: preset?.color ?? palette.color,
      bgColor: preset?.bgColor ?? palette.bgColor
    }
  })
})

const unreadCount = computed(() =>
  messages.value.filter((m) => !m.is_read).length
)

const emptyLine: EChartsOption = buildLineOption([], [])
const emptyBar: EChartsOption = buildBarOption([], [])
const emptyPie: EChartsOption = buildPieOption([])
const emptyRose: EChartsOption = buildRosePieOption([])

const GAUGE_TRACK_COLORS = ['#D6E7FF', '#D6F5F5', '#E8DFF5', '#FFE8D1', '#D9F5D6', '#FCDCEC']

/** 设备状态 → 同心 KPI 环（占比） */
function buildDeviceStatusGauge(
  rows: { device_status: string; count: number }[]
): EChartsOption {
  const total = rows.reduce((sum, r) => sum + (Number(r.count) || 0), 0)
  if (!total) return emptyPie
  const rings: MultiKpiRing[] = rows.slice(0, 5).map((r, i) => {
    const count = Number(r.count) || 0
    const pct = Math.round((count / total) * 1000) / 10
    return {
      name: resolveDictLabel('device_status', r.device_status) ?? String(r.device_status ?? '未知'),
      value: pct,
      color: CHART_COLORS[i % CHART_COLORS.length],
      trackColor: GAUGE_TRACK_COLORS[i % GAUGE_TRACK_COLORS.length]
    }
  })
  return buildMultiKpiGaugeOption(rings, '设备总数', String(total))
}

function chartOptions() {
  void cacheVersion.value
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
    status: buildDeviceStatusGauge(status),
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
  deptValue: { title: '科室资产价值', height: '320px', wide: true }
}

const activeCharts = computed(() => {
  void layoutStore.themeRevision
  const options = chartOptions()
  const keys = profile.value.charts
  const triplePies =
    keys.includes('status') && keys.includes('category') && keys.includes('origin')
  return keys.map((key, index) => {
    const meta = chartMeta[key]
    const isWide = meta.wide ?? false
    const isLastOdd = keys.length % 2 === 1 && index === keys.length - 1
    const isTriplePie =
      triplePies && (key === 'status' || key === 'category' || key === 'origin')
    /** 新增设备与资产分布并排，固定半宽 */
    const isNewDevice = key === 'newDevice'
    return {
      key,
      title: meta.title,
      height: meta.height,
      option: options[key],
      span: isTriplePie ? 8 : isNewDevice ? 12 : isLastOdd ? 24 : isWide ? 12 : 12
    }
  })
})

/** 资产分布：与维修趋势换位后，跟在 origin 后与品牌并排；否则跟新增设备 */
const assetDistAfterKey = computed(() => {
  const keys = profile.value.charts
  if (keys.includes('origin') && keys.includes('brand')) return 'origin'
  if (keys.includes('newDevice')) return 'newDevice'
  return keys[keys.length - 1] ?? ''
})

const assetDistSpan = computed(() => 12)

watch(() => layoutStore.themeRevision, () => {
  // trigger chart option rebuild via activeCharts dependency
})

onMounted(async () => {
  const user = auth.user
  const menuReq =
    user?.userType === 'platform'
      ? http.get('/system/menus/platform-nav')
      : http.get('/system/menus/effective', {
          params: {
            tenantId: user?.tenantId,
            schema: user?.schemaName,
            userId: user?.userId
          }
        })

  const [dash, todoRes, msgRes, menuRes, prefRes] = await Promise.all([
    http.get('/analytics/dashboard'),
    http.get('/analytics/dashboard/todos'),
    http.get('/notification/messages'),
    menuReq.catch(() => ({ data: { data: [] } })),
    user?.userType === 'platform'
      ? Promise.resolve({ data: { data: {} } })
      : http.get('/system/users/me/preferences').catch(() => ({ data: { data: {} } })),
    loadDict('device_status').catch(() => [])
  ])
  stats.value = dash.data.data ?? {}
  todos.value = todoRes.data.data ?? []
  messages.value = msgRes.data.data ?? []
  navModules.value = normalizeNavModules(menuRes.data?.data ?? [])
  const rawPaths = prefRes.data?.data?.quickEntryPaths
  savedQuickPaths.value = Array.isArray(rawPaths) ? (rawPaths as string[]) : null
  startTabRotate()
})

onUnmounted(() => {
  stopTabRotate()
})

function onQuickEntrySaved(paths: string[]) {
  savedQuickPaths.value = paths
}

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

/* 快捷入口 / 待办事项同行等高 */
.workspace-panels > .el-col {
  display: flex;
}

.workspace-panels .panel-card {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.workspace-panels .panel-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.panel-card--fill :deep(.feed-list) {
  flex: 1;
  min-height: 0;
  overflow: auto;
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

.panel-header--with-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.panel-header-action {
  margin: -4px -8px -4px 0;
  padding: 4px 8px;
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
