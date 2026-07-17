<template>
  <div class="twin-screen">
    <div class="twin-bg">
      <div class="twin-grid" />
      <div class="twin-glow twin-glow--tl" />
      <div class="twin-glow twin-glow--br" />
    </div>

    <header class="twin-header">
      <div class="twin-header__side">
        <span class="twin-header__deco" />
        <span class="twin-header__tag">DIGITAL TWIN</span>
      </div>
      <div class="twin-header__center">
        <h1 class="twin-title">库房数字孪生监控中心</h1>
        <p class="twin-subtitle">{{ hospitalName }} · 医药仓储五区三色数字孪生</p>
      </div>
      <div class="twin-header__side twin-header__side--right">
        <span class="twin-clock">{{ nowText }}</span>
        <button class="twin-btn" :disabled="loading" @click="load">
          {{ loading ? '刷新中' : '刷新' }}
        </button>
        <button class="twin-btn twin-btn--primary" @click="exitFullscreen">退出全屏</button>
      </div>
    </header>

    <section class="twin-main">
      <div class="twin-canvas panel">
        <div class="panel__head">
          <span class="panel__title">三维库房 · 五区三色</span>
          <span class="panel__badge">GSP</span>
        </div>
        <div class="twin-canvas__body">
          <WarehouseTwinScene
            :devices="activeDevices"
            :warehouse-name="activeWarehouse?.warehouse_name"
            @select="onTwinSelect"
            @zones-change="onZonesChange"
          />
        </div>
      </div>

      <aside class="twin-aside panel">
        <div class="panel__head">
          <span class="panel__title">库房控制台</span>
        </div>
        <div class="twin-aside__body">
          <label class="field-label">当前库房</label>
          <select v-model="selectedWarehouseId" class="twin-select">
            <option
              v-for="w in warehouses"
              :key="String(w.id)"
              :value="String(w.id)"
            >
              {{ w.warehouse_name }}（{{ w.deviceCount ?? 0 }}）
            </option>
          </select>

          <div class="summary">
            <div class="summary__row">
              <span>在库设备</span>
              <strong>{{ activeWarehouse?.deviceCount ?? activeDevices.length }}</strong>
            </div>
            <div class="field-label">五区分布</div>
            <div class="legend">
              <div v-for="item in zoneLegend" :key="item.code" class="legend__item">
                <span class="legend__dot" :style="{ background: item.colorHex }" />
                <span class="legend__zone">
                  <em>{{ item.colorName }}</em>{{ item.name }}
                </span>
                <span class="legend__val">{{ item.count }}</span>
              </div>
            </div>
            <div class="color-legend">
              <div class="color-legend__item">
                <span class="swatch swatch--yellow" />黄：待验 / 退货待处理
              </div>
              <div class="color-legend__item">
                <span class="swatch swatch--green" />绿：合格 / 发货可放行
              </div>
              <div class="color-legend__item">
                <span class="swatch swatch--red" />红：不合格禁流通
              </div>
            </div>
          </div>

          <div class="field-label field-label--mt">
            {{
              selection
                ? `${selection.zoneColorName}·${selection.zoneName} · 货架设备`
                : '点击货架查看该区设备'
            }}
          </div>
          <div class="device-list">
            <table v-if="selection?.devices?.length" class="data-table">
              <thead>
                <tr>
                  <th>编码</th>
                  <th>名称</th>
                  <th>科室</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="d in selection.devices" :key="String(d.id)">
                  <td class="mono">{{ d.device_code }}</td>
                  <td class="ellipsis" :title="String(d.device_name ?? '')">{{ d.device_name }}</td>
                  <td>{{ d.dept_name || '—' }}</td>
                  <td>{{ statusLabel(d.device_status) }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="hint">
              场景按医药仓储「五区三色」划分：待验区、合格品区、发货区、退货区、不合格品区。货架外形对标医院库房实拍银灰开孔货架。
            </div>
          </div>

          <div class="tip">
            演示映射：待验收→待验区；正常→合格品区/发货区；维修·闲置→退货区；报废→不合格品区。后续可接真实库位主数据。
          </div>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import http from '@/api/http'
import WarehouseTwinScene, {
  type TwinDevice,
  type TwinShelfSelect,
  type ZoneStat
} from '@/components/screen/WarehouseTwinScene.vue'
import { useLayoutStore } from '@/stores/layout'
import { useAuthStore } from '@/stores/auth'

interface TwinWarehouse {
  id: string
  warehouse_code?: string
  warehouse_name?: string
  deviceCount?: number
  statusCounts?: Record<string, number>
  virtual?: boolean
}

const DEVICE_STATUS_MAP: Record<string, string> = {
  normal: '正常',
  maintenance: '维修中',
  pending_verify: '待验收',
  scrap: '报废',
  idle: '闲置',
  unknown: '未知'
}

const layoutStore = useLayoutStore()
const authStore = useAuthStore()
const loading = ref(false)
const warehouses = ref<TwinWarehouse[]>([])
const devicesByWarehouse = ref<Record<string, TwinDevice[]>>({})
const selectedWarehouseId = ref('')
const selection = ref<TwinShelfSelect | null>(null)
const zoneLegend = ref<ZoneStat[]>([])
const nowText = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const hospitalName = computed(() => {
  const code = authStore.user?.tenantCode
  return code ? `${code.toUpperCase()} 智慧医院` : '智慧医院'
})

const activeWarehouse = computed(
  () =>
    warehouses.value.find((w) => String(w.id) === selectedWarehouseId.value) ??
    warehouses.value[0] ??
    null
)

const activeDevices = computed<TwinDevice[]>(() => {
  const id = selectedWarehouseId.value || String(activeWarehouse.value?.id ?? '')
  return devicesByWarehouse.value[id] ?? []
})

function statusLabel(s?: string) {
  return DEVICE_STATUS_MAP[String(s)] || String(s ?? '—')
}

function onTwinSelect(payload: TwinShelfSelect | null) {
  selection.value = payload
}

function onZonesChange(zones: ZoneStat[]) {
  zoneLegend.value = zones
}

watch(selectedWarehouseId, () => {
  selection.value = null
})

function tickClock() {
  nowText.value = new Date().toLocaleString('zh-CN', {
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
    const { data } = await http.get('/screen/equipment/warehouse-twin')
    const twin = data.data ?? {}
    warehouses.value = (twin.warehouses as TwinWarehouse[]) ?? []
    devicesByWarehouse.value = (twin.devicesByWarehouse as Record<string, TwinDevice[]>) ?? {}
    if (
      !selectedWarehouseId.value ||
      !warehouses.value.some((w) => String(w.id) === selectedWarehouseId.value)
    ) {
      selectedWarehouseId.value = warehouses.value[0] ? String(warehouses.value[0].id) : ''
    }
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
.twin-screen {
  position: relative;
  min-height: calc(100vh - 24px);
  padding: 10px 14px 16px;
  color: #d8f0ff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.twin-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(ellipse 80% 50% at 50% -10%, rgba(0, 120, 220, 0.2), transparent 60%),
    linear-gradient(180deg, #030a18 0%, #061428 45%, #040e1e 100%);
}

.twin-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 180, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 180, 255, 0.045) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 90% 80% at 50% 40%, black 20%, transparent 80%);
}

.twin-glow {
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}

.twin-glow--tl {
  top: -120px;
  left: -80px;
  background: rgba(0, 140, 255, 0.28);
}

.twin-glow--br {
  bottom: -100px;
  right: -60px;
  background: rgba(0, 255, 200, 0.12);
}

.twin-header,
.twin-main {
  position: relative;
  z-index: 1;
}

.twin-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  padding: 8px 4px 12px;
  border-bottom: 1px solid rgba(0, 180, 255, 0.2);
}

.twin-header__side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.twin-header__side--right {
  justify-content: flex-end;
}

.twin-header__deco {
  width: 80px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00d4ff);
}

.twin-header__tag {
  font-size: 11px;
  letter-spacing: 3px;
  color: rgba(0, 212, 255, 0.75);
  font-weight: 600;
}

.twin-header__center {
  text-align: center;
}

.twin-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 4px;
  background: linear-gradient(180deg, #ffffff 0%, #7ec8ff 55%, #00ffc6 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.twin-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: rgba(142, 200, 255, 0.75);
}

.twin-clock {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  color: #00ffc6;
  letter-spacing: 1px;
}

.twin-btn {
  padding: 5px 14px;
  font-size: 12px;
  color: #8ec8ff;
  background: rgba(0, 80, 140, 0.35);
  border: 1px solid rgba(0, 180, 255, 0.35);
  border-radius: 4px;
  cursor: pointer;
}

.twin-btn:hover:not(:disabled) {
  background: rgba(0, 120, 200, 0.45);
  color: #fff;
}

.twin-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.twin-btn--primary {
  color: #061428;
  background: linear-gradient(135deg, #00d4ff, #00a8e8);
  border-color: transparent;
  font-weight: 600;
}

.twin-main {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 12px;
}

.panel {
  background: linear-gradient(180deg, rgba(6, 24, 50, 0.92), rgba(4, 14, 32, 0.95));
  border: 1px solid rgba(0, 140, 220, 0.22);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(0, 140, 220, 0.15);
}

.panel__title {
  font-size: 14px;
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
  border-radius: 2px;
  background: linear-gradient(180deg, #00d4ff, #00ffc6);
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

.twin-canvas__body {
  flex: 1;
  min-height: 480px;
  padding: 8px;
}

.twin-aside__body {
  flex: 1;
  min-height: 0;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.field-label {
  font-size: 11px;
  color: rgba(142, 200, 255, 0.7);
  letter-spacing: 1px;
  margin-bottom: 6px;
}

.field-label--mt {
  margin-top: 14px;
}

.twin-select {
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  color: #d8f0ff;
  background: rgba(0, 40, 80, 0.55);
  border: 1px solid rgba(0, 180, 255, 0.35);
  border-radius: 4px;
}

.twin-select option {
  background: #061428;
}

.summary {
  margin-top: 12px;
}

.summary__row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: rgba(142, 200, 255, 0.85);
  margin-bottom: 10px;
}

.summary__row strong {
  color: #00d4ff;
  font-size: 22px;
}

.legend {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.legend__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend__zone {
  flex: 1;
  color: rgba(220, 235, 255, 0.9);
}

.legend__zone em {
  font-style: normal;
  display: inline-block;
  min-width: 1.2em;
  margin-right: 4px;
  font-weight: 700;
  color: #fff;
}

.legend__val {
  margin-left: auto;
  font-weight: 700;
  color: #a8dcff;
}

.color-legend {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 140, 220, 0.15);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.color-legend__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: rgba(180, 200, 220, 0.75);
}

.swatch {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  flex-shrink: 0;
}

.swatch--yellow { background: #fcc419; }
.swatch--green { background: #40c057; }
.swatch--red { background: #fa5252; }

.device-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  margin-top: 6px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.data-table th {
  padding: 6px;
  text-align: left;
  color: rgba(142, 200, 255, 0.7);
  border-bottom: 1px solid rgba(0, 140, 220, 0.2);
}

.data-table td {
  padding: 7px 6px;
  color: #c8e8ff;
  border-bottom: 1px solid rgba(0, 100, 180, 0.1);
}

.mono {
  font-family: Consolas, monospace;
  font-size: 11px;
}

.ellipsis {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hint,
.tip {
  font-size: 12px;
  color: rgba(142, 200, 255, 0.5);
  line-height: 1.55;
}

.tip {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 140, 220, 0.15);
  font-size: 11px;
}

@media (max-width: 1100px) {
  .twin-main {
    grid-template-columns: 1fr;
  }
  .twin-canvas__body {
    min-height: 360px;
  }
  .twin-header {
    grid-template-columns: 1fr;
    text-align: center;
  }
  .twin-header__side--right {
    justify-content: center;
  }
}
</style>
