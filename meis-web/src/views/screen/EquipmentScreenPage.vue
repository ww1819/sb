<template>
  <div class="ops-screen">
    <div class="ops-bg">
      <div class="ops-grid" />
      <div class="ops-glow ops-glow--tl" />
      <div class="ops-glow ops-glow--br" />
      <div class="ops-scanline" />
    </div>

    <header class="ops-header">
      <div class="ops-header__side">
        <span class="ops-header__deco" />
        <span class="ops-header__tag">SMART HOSPITAL</span>
      </div>
      <div class="ops-header__center">
        <h1 class="ops-title">医疗设备智慧运营监控中心</h1>
        <p class="ops-subtitle">
          {{ hospitalName }} · 全院设备实时态势感知
        </p>
      </div>
      <div class="ops-header__side ops-header__side--right">
        <span class="ops-clock">{{ nowText }}</span>
        <span class="ops-header__deco ops-header__deco--right" />
        <button class="ops-btn" :disabled="loading" @click="load">
          {{ loading ? '刷新中' : '刷新' }}
        </button>
        <button class="ops-btn ops-btn--primary" @click="exitFullscreen">退出全屏</button>
      </div>
    </header>

    <section class="ops-kpis">
      <div
        v-for="item in kpiItems"
        :key="item.key"
        class="kpi"
        :class="{ 'kpi--warn': item.warn && Number(kpis[item.key] ?? 0) > 0 }"
      >
        <div class="kpi__corner kpi__corner--tl" />
        <div class="kpi__corner kpi__corner--tr" />
        <div class="kpi__corner kpi__corner--bl" />
        <div class="kpi__corner kpi__corner--br" />
        <div class="kpi__icon" :class="`kpi__icon--${item.icon}`" />
        <div class="kpi__body">
          <div class="kpi__label">{{ item.label }}</div>
          <div class="kpi__value">
            <span class="kpi__num">{{ formatKpi(item.key) }}</span>
            <span v-if="item.unit" class="kpi__unit">{{ item.unit }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="ops-charts">
      <div class="panel panel--chart">
        <div class="panel__head">
          <span class="panel__title">设备状态分布</span>
          <span class="panel__badge">LIVE</span>
        </div>
        <div class="panel__body">
          <ChartCard title="" :option="deviceStatusOption" height="100%" />
        </div>
      </div>
      <div class="panel panel--chart">
        <div class="panel__head">
          <span class="panel__title">科室设备 TOP12</span>
          <span class="panel__badge">RANK</span>
        </div>
        <div class="panel__body">
          <ChartCard title="" :option="deptOption" height="100%" />
        </div>
      </div>
      <div class="panel panel--chart">
        <div class="panel__head">
          <span class="panel__title">维修工单趋势</span>
          <span class="panel__badge">12M</span>
        </div>
        <div class="panel__body">
          <ChartCard title="" :option="repairTrendOption" height="100%" />
        </div>
      </div>
    </section>

    <section class="ops-bottom">
      <div class="panel panel--table">
        <div class="panel__head">
          <span class="panel__title">维修动态</span>
          <span class="panel__hint">实时滚动</span>
        </div>
        <div class="panel__body panel__body--scroll">
          <div class="scroll-track" :class="{ 'scroll-track--pause': !repairDynamic.length }">
            <table class="data-table">
              <thead>
                <tr>
                  <th>工单号</th>
                  <th>设备</th>
                  <th>科室</th>
                  <th>状态</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in scrollRepairRows" :key="`${row.wo_no}-${i}`">
                  <td class="mono">{{ row.wo_no }}</td>
                  <td class="ellipsis" :title="String(row.device_name ?? '')">{{ row.device_name }}</td>
                  <td>{{ row.dept_name || '—' }}</td>
                  <td>
                    <span class="status-pill" :class="statusClass(row.status)">
                      {{ statusLabel(row.status) }}
                    </span>
                  </td>
                  <td class="mono dim">{{ formatTime(row.created_at) }}</td>
                </tr>
                <tr v-if="!repairDynamic.length">
                  <td colspan="5" class="empty">暂无进行中的维修工单</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="panel panel--table">
        <div class="panel__head">
          <span class="panel__title">质控到期预警</span>
          <span class="panel__hint">7 日内</span>
        </div>
        <div class="panel__body">
          <table class="data-table">
            <thead>
              <tr>
                <th>类型</th>
                <th>设备</th>
                <th>到期日</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in qcDueList" :key="i">
                <td><span class="qc-tag">{{ row.qc_type }}</span></td>
                <td class="ellipsis" :title="String(row.device_name ?? '')">{{ row.device_name }}</td>
                <td class="warn-date">{{ formatDate(row.due_date) }}</td>
              </tr>
              <tr v-if="!qcDueList.length">
                <td colspan="3" class="empty">近 7 日无到期项</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="panel panel--side">
        <div class="panel__head">
          <span class="panel__title">运营洞察</span>
        </div>
        <div class="panel__body panel__body--stack">
          <div class="mini-grid">
            <div v-for="p in purchaseStats" :key="p.key" class="mini-stat">
              <div class="mini-stat__val">{{ p.value }}</div>
              <div class="mini-stat__lbl">{{ p.label }}</div>
            </div>
          </div>

          <div class="sub-block">
            <div class="sub-block__title">本月效益 TOP</div>
            <table class="data-table data-table--compact">
              <thead>
                <tr>
                  <th>设备</th>
                  <th>净利润</th>
                  <th>等级</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in benefitTop" :key="i">
                  <td class="ellipsis" :title="String(row.device_name ?? '')">{{ row.device_name }}</td>
                  <td class="profit">{{ formatMoney(row.net_profit) }}</td>
                  <td>
                    <span class="benefit-tag" :class="benefitClass(row.benefit_level)">
                      {{ row.benefit_level || '—' }}
                    </span>
                  </td>
                </tr>
                <tr v-if="!benefitTop.length">
                  <td colspan="3" class="empty">暂无效益数据</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="sub-block power-block">
            <div class="sub-block__title">电流监测态势</div>
            <div class="power-metrics">
              <div class="power-metric">
                <span class="power-metric__val">{{ powerOverview.runningCount ?? 0 }}</span>
                <span class="power-metric__lbl">运行中</span>
              </div>
              <div class="power-metric">
                <span class="power-metric__val">{{ formatEnergy(powerOverview.todayEnergyKwh) }}</span>
                <span class="power-metric__lbl">今日用电 kWh</span>
              </div>
            </div>
            <div class="power-states">
              <div
                v-for="s in powerStates"
                :key="s.name"
                class="power-state"
              >
                <span class="power-state__dot" :class="`power-state__dot--${s.key}`" />
                <span class="power-state__name">{{ s.label }}</span>
                <span class="power-state__val">{{ s.value }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import http from '@/api/http'
import ChartCard from '@/components/dashboard/ChartCard.vue'
import { useLayoutStore } from '@/stores/layout'
import { useAuthStore } from '@/stores/auth'

const layoutStore = useLayoutStore()
const authStore = useAuthStore()
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

const hospitalName = computed(() => {
  const code = authStore.user?.tenantCode
  return code ? `${code.toUpperCase()} 智慧医院` : '智慧医院'
})

const kpiItems = [
  { key: 'deviceCount', label: '在用设备', icon: 'device', unit: '台' },
  { key: 'openRepairs', label: '在修工单', icon: 'repair', warn: true },
  { key: 'activeMaintenance', label: '保养计划', icon: 'maintain' },
  { key: 'dueQcCount', label: '7日到期', icon: 'qc', warn: true },
  { key: 'alarmDevices', label: '电流告警', icon: 'alarm', warn: true },
  { key: 'pendingApprovals', label: '待审批', icon: 'approval' },
  { key: 'lifeSupportCount', label: '生命支持', icon: 'life', unit: '台' },
  { key: 'totalAssetValue', label: '资产总值', icon: 'asset', unit: '万' }
]

const STATUS_MAP: Record<string, string> = {
  reported: '已报修',
  dispatching: '派工中',
  pending_accept: '待接单',
  accepted: '已接单',
  repairing: '维修中',
  pending_verify: '待验收',
  suspended: '挂起',
  verify_rejected: '验收驳回',
  verified: '已验收',
  closed: '已关闭',
  cancelled: '已取消',
  draft: '草稿'
}

const DEVICE_STATUS_MAP: Record<string, string> = {
  normal: '正常',
  maintenance: '维修中',
  pending_verify: '待验收',
  scrap: '报废',
  idle: '闲置'
}

const POWER_STATE_MAP: Record<string, string> = {
  running: '运行',
  standby: '待机',
  offline: '离线',
  alarm: '告警',
  unknown: '未知'
}

const CHART_COLORS = ['#00d4ff', '#3d8bff', '#00ffc6', '#ffd166', '#ff6b8a', '#a78bfa', '#4ade80', '#fb923c']

const darkBase = {
  backgroundColor: 'transparent',
  textStyle: { color: '#8ec8ff', fontFamily: 'inherit' }
}

const deviceStatusOption = computed<EChartsOption>(() => ({
  ...darkBase,
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(6, 20, 45, 0.92)',
    borderColor: 'rgba(0, 212, 255, 0.35)',
    textStyle: { color: '#d8f0ff' }
  },
  legend: {
    bottom: 0,
    textStyle: { color: '#7eb8e8', fontSize: 11 },
    itemWidth: 10,
    itemHeight: 10
  },
  series: [{
    type: 'pie',
    radius: ['48%', '72%'],
    center: ['50%', '46%'],
    padAngle: 2,
    itemStyle: {
      borderRadius: 4,
      borderColor: 'rgba(6, 18, 40, 0.8)',
      borderWidth: 2
    },
    label: {
      color: '#b8dcff',
      fontSize: 11,
      formatter: '{b}\n{d}%'
    },
    emphasis: {
      scale: true,
      itemStyle: { shadowBlur: 18, shadowColor: 'rgba(0, 212, 255, 0.45)' }
    },
    data: deviceStatus.value.map((r, i) => ({
      name: DEVICE_STATUS_MAP[String(r.name)] || String(r.name),
      value: r.value,
      itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] }
    }))
  }]
}))

const deptOption = computed<EChartsOption>(() => ({
  ...darkBase,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(6, 20, 45, 0.92)',
    borderColor: 'rgba(0, 212, 255, 0.35)',
    textStyle: { color: '#d8f0ff' }
  },
  grid: { left: 8, right: 12, top: 16, bottom: 4, containLabel: true },
  xAxis: {
    type: 'value',
    axisLabel: { color: '#6ea8d8', fontSize: 10 },
    splitLine: { lineStyle: { color: 'rgba(0, 180, 255, 0.08)' } }
  },
  yAxis: {
    type: 'category',
    data: [...deptDistribution.value].reverse().map(r => String(r.name)),
    axisLabel: { color: '#9ecfff', fontSize: 10 },
    axisLine: { show: false },
    axisTick: { show: false }
  },
  series: [{
    type: 'bar',
    data: [...deptDistribution.value].reverse().map((r, i) => ({
      value: r.value,
      itemStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [
            { offset: 0, color: 'rgba(0, 212, 255, 0.25)' },
            { offset: 1, color: CHART_COLORS[i % CHART_COLORS.length] }
          ]
        },
        borderRadius: [0, 4, 4, 0]
      }
    })),
    barWidth: 10,
    showBackground: true,
    backgroundStyle: { color: 'rgba(0, 80, 140, 0.15)', borderRadius: [0, 4, 4, 0] }
  }]
}))

const repairTrendOption = computed<EChartsOption>(() => ({
  ...darkBase,
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(6, 20, 45, 0.92)',
    borderColor: 'rgba(0, 212, 255, 0.35)',
    textStyle: { color: '#d8f0ff' }
  },
  grid: { left: 8, right: 12, top: 20, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    data: repairTrend.value.map(r => String(r.month).slice(5)),
    axisLabel: { color: '#6ea8d8', fontSize: 10 },
    axisLine: { lineStyle: { color: 'rgba(0, 180, 255, 0.2)' } },
    axisTick: { show: false }
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#6ea8d8', fontSize: 10 },
    splitLine: { lineStyle: { color: 'rgba(0, 180, 255, 0.08)' } }
  },
  series: [{
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    data: repairTrend.value.map(r => r.count),
    lineStyle: { width: 2, color: '#00ffc6' },
    itemStyle: { color: '#00ffc6', borderColor: '#061428', borderWidth: 2 },
    areaStyle: {
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: 'rgba(0, 255, 198, 0.35)' },
          { offset: 1, color: 'rgba(0, 255, 198, 0.02)' }
        ]
      }
    }
  }]
}))

const scrollRepairRows = computed(() => {
  if (!repairDynamic.value.length) return []
  return [...repairDynamic.value, ...repairDynamic.value]
})

const purchaseStats = computed(() => [
  { key: 'pendingPlans', label: '待审计划', value: purchaseBrief.value.pendingPlans ?? 0 },
  { key: 'activeContracts', label: '生效合同', value: purchaseBrief.value.activeContracts ?? 0 },
  { key: 'pendingAcceptance', label: '待验收', value: purchaseBrief.value.pendingAcceptance ?? 0 },
  { key: 'monthEntries', label: '本月入库', value: purchaseBrief.value.monthEntries ?? 0 }
])

const powerStates = computed(() => {
  const rows = (powerOverview.value.stateCounts as Record<string, unknown>[]) ?? []
  return rows.map(r => ({
    key: String(r.name ?? 'unknown'),
    label: POWER_STATE_MAP[String(r.name)] || String(r.name),
    value: r.value ?? 0
  }))
})

function formatKpi(key: string) {
  const v = kpis.value[key]
  if (key === 'totalAssetValue') return (Number(v ?? 0) / 10000).toFixed(1)
  return Number(v ?? 0).toLocaleString('zh-CN')
}

function formatMoney(v: unknown) {
  const n = Number(v ?? 0)
  if (Math.abs(n) >= 10000) return `${(n / 10000).toFixed(1)}万`
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function formatEnergy(v: unknown) {
  return Number(v ?? 0).toFixed(1)
}

function formatTime(v: unknown) {
  if (!v) return '—'
  const d = new Date(String(v))
  if (Number.isNaN(d.getTime())) return String(v).slice(0, 16)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

function formatDate(v: unknown) {
  if (!v) return '—'
  return String(v).slice(0, 10)
}

function statusLabel(s: unknown) {
  return STATUS_MAP[String(s)] || String(s ?? '—')
}

function statusClass(s: unknown) {
  const key = String(s)
  if (['repairing', 'pending_verify', 'verify_rejected'].includes(key)) return 'status-pill--active'
  if (['reported', 'dispatching', 'pending_accept'].includes(key)) return 'status-pill--pending'
  return 'status-pill--default'
}

function benefitClass(level: unknown) {
  const l = String(level ?? '')
  if (l.includes('优') || l === 'A') return 'benefit-tag--a'
  if (l.includes('良') || l === 'B') return 'benefit-tag--b'
  return 'benefit-tag--c'
}

function tickClock() {
  const d = new Date()
  nowText.value = d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
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
  authStore.restore()
  layoutStore.setContentFullscreen(true)
  tickClock()
  load()
  timer = setInterval(() => {
    tickClock()
    load()
  }, 60000)
})

onUnmounted(() => {
  layoutStore.setContentFullscreen(false)
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.ops-screen {
  position: relative;
  min-height: calc(100vh - 24px);
  padding: 10px 14px 16px;
  color: #d8f0ff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ── Background ── */
.ops-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(ellipse 80% 50% at 50% -10%, rgba(0, 120, 220, 0.18), transparent 60%),
    radial-gradient(ellipse 60% 40% at 100% 100%, rgba(0, 255, 200, 0.06), transparent 50%),
    linear-gradient(180deg, #030a18 0%, #061428 40%, #040e1e 100%);
}

.ops-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 180, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 180, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 90% 80% at 50% 40%, black 20%, transparent 80%);
}

.ops-glow {
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}

.ops-glow--tl {
  top: -120px;
  left: -80px;
  background: rgba(0, 140, 255, 0.25);
}

.ops-glow--br {
  bottom: -100px;
  right: -60px;
  background: rgba(0, 255, 200, 0.12);
}

.ops-scanline {
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 3px,
    rgba(0, 180, 255, 0.015) 3px,
    rgba(0, 180, 255, 0.015) 4px
  );
  animation: scan-drift 8s linear infinite;
}

@keyframes scan-drift {
  from { transform: translateY(0); }
  to { transform: translateY(48px); }
}

/* ── Header ── */
.ops-header,
.ops-kpis,
.ops-charts,
.ops-bottom {
  position: relative;
  z-index: 1;
}

.ops-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  padding: 8px 4px 12px;
  border-bottom: 1px solid rgba(0, 180, 255, 0.2);
}

.ops-header__side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ops-header__side--right {
  justify-content: flex-end;
}

.ops-header__deco {
  display: block;
  width: 80px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00d4ff);
}

.ops-header__deco--right {
  background: linear-gradient(90deg, #00d4ff, transparent);
}

.ops-header__tag {
  font-size: 11px;
  letter-spacing: 3px;
  color: rgba(0, 212, 255, 0.7);
  font-weight: 600;
}

.ops-header__center {
  text-align: center;
}

.ops-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 4px;
  background: linear-gradient(180deg, #ffffff 0%, #7ec8ff 55%, #00d4ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 0 40px rgba(0, 212, 255, 0.3);
  animation: title-glow 3s ease-in-out infinite alternate;
}

@keyframes title-glow {
  from { filter: drop-shadow(0 0 8px rgba(0, 212, 255, 0.3)); }
  to { filter: drop-shadow(0 0 18px rgba(0, 212, 255, 0.55)); }
}

.ops-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: rgba(142, 200, 255, 0.75);
  letter-spacing: 1px;
}

.ops-clock {
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  color: #00ffc6;
  letter-spacing: 1px;
  text-shadow: 0 0 12px rgba(0, 255, 198, 0.4);
}

.ops-btn {
  padding: 5px 14px;
  font-size: 12px;
  color: #8ec8ff;
  background: rgba(0, 80, 140, 0.35);
  border: 1px solid rgba(0, 180, 255, 0.35);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.ops-btn:hover:not(:disabled) {
  background: rgba(0, 120, 200, 0.45);
  border-color: rgba(0, 212, 255, 0.6);
  color: #fff;
}

.ops-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ops-btn--primary {
  color: #061428;
  background: linear-gradient(135deg, #00d4ff, #00a8e8);
  border-color: transparent;
  font-weight: 600;
}

/* ── KPIs ── */
.ops-kpis {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}

.kpi {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 10px;
  background: linear-gradient(135deg, rgba(8, 32, 64, 0.85), rgba(4, 18, 40, 0.9));
  border: 1px solid rgba(0, 140, 220, 0.25);
  overflow: hidden;
}

.kpi::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.5), transparent);
}

.kpi--warn {
  border-color: rgba(255, 120, 80, 0.45);
  animation: warn-pulse 2s ease-in-out infinite;
}

.kpi--warn .kpi__num {
  color: #ff9f7f;
  text-shadow: 0 0 16px rgba(255, 120, 80, 0.5);
}

@keyframes warn-pulse {
  0%, 100% { box-shadow: inset 0 0 20px rgba(255, 80, 40, 0.05); }
  50% { box-shadow: inset 0 0 30px rgba(255, 80, 40, 0.12); }
}

.kpi__corner {
  position: absolute;
  width: 8px;
  height: 8px;
  border-color: #00d4ff;
  border-style: solid;
  opacity: 0.7;
}

.kpi__corner--tl { top: 0; left: 0; border-width: 2px 0 0 2px; }
.kpi__corner--tr { top: 0; right: 0; border-width: 2px 2px 0 0; }
.kpi__corner--bl { bottom: 0; left: 0; border-width: 0 0 2px 2px; }
.kpi__corner--br { bottom: 0; right: 0; border-width: 0 2px 2px 0; }

.kpi__icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(0, 140, 220, 0.2);
  border: 1px solid rgba(0, 180, 255, 0.3);
  flex-shrink: 0;
  position: relative;
}

.kpi__icon::after {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 50%;
  background: radial-gradient(circle, #00d4ff 0%, transparent 70%);
  opacity: 0.6;
}

.kpi__label {
  font-size: 11px;
  color: rgba(142, 200, 255, 0.8);
  margin-bottom: 2px;
}

.kpi__value {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.kpi__num {
  font-size: 22px;
  font-weight: 800;
  color: #00d4ff;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 20px rgba(0, 212, 255, 0.35);
  line-height: 1;
}

.kpi__unit {
  font-size: 11px;
  color: rgba(142, 200, 255, 0.6);
}

/* ── Panels ── */
.ops-charts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  flex: 1;
  min-height: 0;
}

.ops-bottom {
  display: grid;
  grid-template-columns: 1.2fr 0.9fr 1fr;
  gap: 10px;
  min-height: 220px;
}

.panel {
  background: linear-gradient(180deg, rgba(6, 24, 50, 0.92), rgba(4, 14, 32, 0.95));
  border: 1px solid rgba(0, 140, 220, 0.22);
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
}

.panel::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.35), transparent);
}

.panel--chart {
  min-height: 220px;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(0, 140, 220, 0.15);
  flex-shrink: 0;
}

.panel__title {
  font-size: 13px;
  font-weight: 700;
  color: #a8dcff;
  letter-spacing: 1px;
  padding-left: 10px;
  position: relative;
}

.panel__title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 1px;
  bottom: 1px;
  width: 3px;
  background: linear-gradient(180deg, #00d4ff, #00ffc6);
  border-radius: 2px;
  box-shadow: 0 0 8px rgba(0, 212, 255, 0.5);
}

.panel__badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(0, 212, 255, 0.12);
  border: 1px solid rgba(0, 212, 255, 0.3);
  color: #00d4ff;
  letter-spacing: 1px;
}

.panel__hint {
  font-size: 11px;
  color: rgba(142, 200, 255, 0.5);
}

.panel__body {
  flex: 1;
  min-height: 0;
  padding: 4px 8px 8px;
  overflow: hidden;
}

.panel__body--scroll {
  overflow: hidden;
  max-height: 200px;
}

.panel__body--stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.panel :deep(.chart-card) {
  background: transparent;
  border: none;
  box-shadow: none;
  height: 100%;
}

.panel :deep(.el-card__header) { display: none; }
.panel :deep(.el-card__body) {
  padding: 0;
  height: 100%;
}

.panel :deep(.chart-card-body) {
  height: 200px !important;
}

/* ── Tables ── */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.data-table th {
  padding: 6px 8px;
  text-align: left;
  color: rgba(142, 200, 255, 0.7);
  font-weight: 600;
  border-bottom: 1px solid rgba(0, 140, 220, 0.2);
  white-space: nowrap;
}

.data-table td {
  padding: 7px 8px;
  color: #c8e8ff;
  border-bottom: 1px solid rgba(0, 100, 180, 0.1);
}

.data-table tbody tr:hover td {
  background: rgba(0, 120, 200, 0.08);
}

.data-table--compact td,
.data-table--compact th {
  padding: 5px 6px;
  font-size: 11px;
}

.mono {
  font-family: 'Consolas', monospace;
  font-size: 11px;
}

.dim { color: rgba(142, 200, 255, 0.55); }

.ellipsis {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  text-align: center;
  color: rgba(142, 200, 255, 0.4);
  padding: 20px !important;
}

/* ── Scroll ── */
.scroll-track {
  animation: scroll-up 28s linear infinite;
}

.scroll-track--pause {
  animation: none;
}

.scroll-track:hover {
  animation-play-state: paused;
}

@keyframes scroll-up {
  0% { transform: translateY(0); }
  100% { transform: translateY(-50%); }
}

/* ── Status pills ── */
.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
}

.status-pill--active {
  background: rgba(255, 120, 80, 0.2);
  color: #ff9f7f;
  border: 1px solid rgba(255, 120, 80, 0.35);
}

.status-pill--pending {
  background: rgba(255, 209, 102, 0.15);
  color: #ffd166;
  border: 1px solid rgba(255, 209, 102, 0.3);
}

.status-pill--default {
  background: rgba(0, 180, 255, 0.12);
  color: #7ec8ff;
  border: 1px solid rgba(0, 180, 255, 0.25);
}

.qc-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(167, 139, 250, 0.15);
  color: #c4b5fd;
  border: 1px solid rgba(167, 139, 250, 0.3);
  font-size: 10px;
}

.warn-date {
  color: #ff9f7f;
  font-weight: 600;
}

/* ── Side panel ── */
.mini-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.mini-stat {
  text-align: center;
  padding: 8px 4px;
  background: rgba(0, 80, 140, 0.2);
  border: 1px solid rgba(0, 140, 220, 0.2);
  border-radius: 4px;
}

.mini-stat__val {
  display: block;
  font-size: 18px;
  font-weight: 800;
  color: #00d4ff;
  line-height: 1.2;
}

.mini-stat__lbl {
  display: block;
  font-size: 10px;
  color: rgba(142, 200, 255, 0.6);
  margin-top: 2px;
}

.sub-block__title {
  font-size: 11px;
  color: rgba(142, 200, 255, 0.7);
  margin-bottom: 6px;
  letter-spacing: 1px;
}

.benefit-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
}

.benefit-tag--a { background: rgba(0, 255, 198, 0.15); color: #00ffc6; }
.benefit-tag--b { background: rgba(0, 212, 255, 0.15); color: #00d4ff; }
.benefit-tag--c { background: rgba(255, 209, 102, 0.12); color: #ffd166; }

.profit {
  color: #00ffc6;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.power-metrics {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.power-metric {
  flex: 1;
  padding: 8px;
  background: rgba(0, 60, 100, 0.25);
  border: 1px solid rgba(0, 140, 220, 0.2);
  border-radius: 4px;
  text-align: center;
}

.power-metric__val {
  display: block;
  font-size: 20px;
  font-weight: 800;
  color: #00ffc6;
  text-shadow: 0 0 12px rgba(0, 255, 198, 0.3);
}

.power-metric__lbl {
  display: block;
  font-size: 10px;
  color: rgba(142, 200, 255, 0.6);
  margin-top: 2px;
}

.power-states {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
}

.power-state {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
}

.power-state__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6ea8d8;
}

.power-state__dot--running { background: #00ffc6; box-shadow: 0 0 8px rgba(0, 255, 198, 0.6); }
.power-state__dot--standby { background: #ffd166; }
.power-state__dot--offline { background: #6ea8d8; }
.power-state__dot--alarm { background: #ff6b8a; box-shadow: 0 0 8px rgba(255, 107, 138, 0.6); animation: warn-pulse 1.5s infinite; }

.power-state__name { color: rgba(142, 200, 255, 0.7); }
.power-state__val { color: #a8dcff; font-weight: 700; }

/* ── Responsive ── */
@media (max-width: 1400px) {
  .ops-kpis { grid-template-columns: repeat(4, 1fr); }
  .ops-title { font-size: 22px; letter-spacing: 2px; }
}

@media (max-width: 1100px) {
  .ops-charts,
  .ops-bottom { grid-template-columns: 1fr; }
  .ops-header { grid-template-columns: 1fr; text-align: center; }
  .ops-header__side--right { justify-content: center; }
}
</style>
