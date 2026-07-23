<template>
  <view class="hub">
    <view class="toolbar">
      <button type="primary" size="mini" @click="scanAndExecute">扫码执行</button>
      <button size="mini" @click="manualCode">手输编码</button>
      <button size="mini" @click="loadDue">刷新到期</button>
    </view>

    <view class="section-label">到期计划明细</view>
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!dueItems.length" class="empty">暂无到期项</view>
    <view
      v-for="(row, idx) in dueItems"
      :key="String(row.id || idx)"
      class="due-item"
      @click="onDueTap(row)"
    >
      <text class="due-title">{{ row.device_name || row.device_code || '设备' }}</text>
      <text class="due-meta">
        {{ row.plan_no || '' }} · 到期 {{ formatDate(row.next_due_date) }}
      </text>
    </view>

    <!-- 通用可选列表（突破 ActionSheet 6 条） -->
    <view v-if="picker.visible" class="overlay" @click="closePicker">
      <view class="sheet" @click.stop>
        <view class="sheet-title">{{ picker.title }}</view>
        <scroll-view scroll-y class="sheet-list">
          <view
            v-for="(row, idx) in picker.rows"
            :key="String(row.key || idx)"
            class="sheet-row"
            @click="onPickRow(idx)"
          >
            <text class="sheet-row-title">{{ row.title }}</text>
            <text v-if="row.subtitle" class="sheet-row-sub">{{ row.subtitle }}</text>
          </view>
          <view v-if="!picker.rows.length" class="empty">暂无数据</view>
        </scroll-view>
        <button size="mini" @click="closePicker">取消</button>
      </view>
    </view>

    <!-- 直开表单 -->
    <view v-if="adhoc.visible" class="overlay" @click="closeAdhoc">
      <view class="sheet adhoc-sheet" @click.stop>
        <view class="sheet-title">无计划直开</view>
        <view class="form-row" @click="pickTemplate">
          <text class="label">模板</text>
          <text class="value">{{ adhoc.templateName || '请选择' }}</text>
        </view>
        <view class="form-row">
          <text class="label">{{ cfg.levelLabel }}</text>
          <input v-model="adhoc.level" class="input" :placeholder="`请填写${cfg.levelLabel}`" />
        </view>
        <view class="form-row">
          <text class="label">周期类型</text>
          <picker :range="cycleTypeLabels" :value="cycleTypeIndex" @change="onCycleTypeChange">
            <text class="value">{{ cycleTypeLabels[cycleTypeIndex] }}</text>
          </picker>
        </view>
        <view class="form-row">
          <text class="label">周期值</text>
          <input v-model="adhoc.cycleValue" class="input" type="number" placeholder="如 1" />
        </view>
        <view class="hint">周期天数：{{ adhocCycleDays ?? '—' }}</view>
        <view class="form-row">
          <text class="label">执行日期</text>
          <picker mode="date" :value="adhoc.plannedDate" @change="onPlannedDateChange">
            <text class="value">{{ adhoc.plannedDate }}</text>
          </picker>
        </view>
        <view class="hint">起止默认 {{ adhoc.plannedDate }} 00:00:00 ～ 23:59:59</view>
        <view class="adhoc-actions">
          <button size="mini" @click="closeAdhoc">取消</button>
          <button type="primary" size="mini" :loading="adhoc.saving" @click="submitAdhoc">创建            创建
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { OPS_MODULES, type OpsModule, type OpsModuleConfig } from '@/config/ops'
import { useAuthStore } from '@/stores/auth'
import { calcCycleDays, todayYmd } from '@/utils/cycleDays'

const auth = useAuthStore()
const moduleKey = ref<OpsModule>('maintain')
const cfg = ref<OpsModuleConfig>(OPS_MODULES.maintain)
const dueItems = ref<Record<string, unknown>[]>([])
const loading = ref(false)

type PickerRow = { key?: string; title: string; subtitle?: string; raw: Record<string, unknown> }
const picker = reactive({
  visible: false,
  title: '',
  rows: [] as PickerRow[],
  onSelect: null as null | ((row: Record<string, unknown>) => void)
})

const cycleTypes = ['day', 'week', 'month', 'year'] as const
const cycleTypeLabels = ['天', '周', '月', '年']
const adhoc = reactive({
  visible: false,
  saving: false,
  device: null as Record<string, unknown> | null,
  templates: [] as Record<string, unknown>[],
  templateId: '',
  templateName: '',
  level: '',
  cycleType: 'month' as string,
  cycleValue: '1',
  plannedDate: todayYmd()
})

const cycleTypeIndex = computed(() => {
  const i = cycleTypes.indexOf(adhoc.cycleType as (typeof cycleTypes)[number])
  return i >= 0 ? i : 2
})

const adhocCycleDays = computed(() =>
  calcCycleDays(adhoc.cycleType, Number(adhoc.cycleValue))
)

onLoad((query) => {
  const m = (query?.module as OpsModule) || 'maintain'
  moduleKey.value = m in OPS_MODULES ? m : 'maintain'
  cfg.value = OPS_MODULES[moduleKey.value]
  uni.setNavigationBarTitle({ title: cfg.value.title })
})

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loadDue()
})

function formatDate(v: unknown) {
  if (!v) return '—'
  return String(v).slice(0, 10)
}

function closePicker() {
  picker.visible = false
  picker.rows = []
  picker.onSelect = null
}

function openPicker(title: string, rows: PickerRow[], onSelect: (row: Record<string, unknown>) => void) {
  picker.title = title
  picker.rows = rows
  picker.onSelect = onSelect
  picker.visible = true
}

function onPickRow(idx: number) {
  const row = picker.rows[idx]
  const cb = picker.onSelect
  closePicker()
  if (row && cb) cb(row.raw)
}

async function loadDue() {
  loading.value = true
  try {
    const raw = await http.get<unknown>(cfg.value.planDuePath)
    const list = Array.isArray(raw) ? raw : (raw as { records?: unknown[] })?.records || []
    dueItems.value = list.map((e) => ({ ...(e as object) })) as Record<string, unknown>[]
  } catch {
    dueItems.value = []
  } finally {
    loading.value = false
  }
}

function scanAndExecute() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const code = (res.result || '').trim()
      if (!code) {
        uni.showToast({ title: '未识别到内容', icon: 'none' })
        return
      }
      openByCode(code)
    },
    fail: () => uni.showToast({ title: '扫码取消或失败', icon: 'none' })
  })
}

function manualCode() {
  uni.showModal({
    title: '设备编码',
    editable: true,
    placeholderText: '输入设备编码',
    success: (res) => {
      if (res.confirm && res.content) openByCode(res.content.trim())
    }
  })
}

async function openByCode(code: string) {
  uni.showLoading({ title: '查找设备' })
  try {
    const list = await http.get<Record<string, unknown>[]>('/repair/workorder/devices/lookup', {
      deviceCode: code
    })
    const devices = Array.isArray(list) ? list : []
    if (!devices.length) {
      uni.showToast({ title: '未找到设备', icon: 'none' })
      return
    }
    if (devices.length === 1) {
      await openDeviceTasks(devices[0])
      return
    }
    openPicker(
      '选择设备',
      devices.map((d) => ({
        key: String(d.id || ''),
        title: String(d.device_name || d.device_code || '设备'),
        subtitle: String(d.device_code || ''),
        raw: d
      })),
      (d) => {
        void openDeviceTasks(d)
      }
    )
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '查找失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function onDueTap(row: Record<string, unknown>) {
  if (row.device_id || row.device_code) {
    await openDeviceTasks({
      id: row.device_id,
      device_code: row.device_code,
      device_name: row.device_name
    })
  }
}

async function openDeviceTasks(device: Record<string, unknown>) {
  const deviceId = device.id?.toString()
  if (!deviceId) return
  uni.showActionSheet({
    itemList: ['新增执行单', '申请纳入计划', '执行明细', '确认明细'],
    success: async (r) => {
      if (r.tapIndex === 0) await createAdHoc(device)
      else if (r.tapIndex === 1) await applyInclude(device)
      else if (r.tapIndex === 2) await pickOpenItem(device, false)
      else if (r.tapIndex === 3) await pickOpenItem(device, true)
    }
  })
}

async function applyInclude(device: Record<string, unknown>) {
  try {
    uni.showLoading({ title: '加载计划' })
    const raw = await http.get<Record<string, unknown>[]>(
      `/${cfg.value.module}/plan/include-request/approved-plans`,
      { device_ids: String(device.id || '') }
    )
    const plans = Array.isArray(raw) ? raw : []
    uni.hideLoading()
    if (!plans.length) {
      uni.showToast({ title: '暂无可申请计划', icon: 'none' })
      return
    }
    openPicker(
      '申请纳入计划',
      plans.map((p) => ({
        key: String(p.id || ''),
        title: String(p.plan_no || p.plan_name || p.id || ''),
        subtitle: [p.plan_name, p.cycle_days != null ? `${p.cycle_days}天` : '']
          .filter(Boolean)
          .join(' · '),
        raw: p
      })),
      async (plan) => {
        try {
          uni.showLoading({ title: '提交中' })
          await http.post(`/${cfg.value.module}/plan/include-request`, {
            client: 'mp',
            plan_id: plan.id,
            device_id: device.id,
            device_code: device.device_code,
            device_name: device.device_name,
            dept_id: device.dept_id
          })
          uni.showToast({ title: '已提交，待 Web 确认', icon: 'success' })
        } catch (e: unknown) {
          uni.showToast({ title: e instanceof Error ? e.message : '提交失败', icon: 'none' })
        } finally {
          uni.hideLoading()
        }
      }
    )
  } catch (e: unknown) {
    uni.hideLoading()
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  }
}

async function pickOpenItem(device: Record<string, unknown>, forConfirm: boolean) {
  const deviceId = device.id?.toString()
  if (!deviceId) return
  try {
    uni.showLoading({ title: '加载明细' })
    const raw = await http.get<Record<string, unknown>[]>(
      `${cfg.value.executionBase}/by-device/${deviceId}`,
      { openOnly: true }
    )
    let items = Array.isArray(raw) ? raw : []
    // 执行入口不列已确认；确认入口也不列已确认
    items = items.filter((it) => String(it.status || '') !== 'confirmed')
    uni.hideLoading()
    if (!items.length) {
      uni.showToast({
        title: forConfirm ? '暂无可确认明细' : '暂无待执行明细',
        icon: 'none'
      })
      return
    }
    openPicker(
      forConfirm ? '确认明细' : '执行明细',
      items.map((it) => ({
        key: String(it.id || ''),
        title: String(it.execution_no || it.id || ''),
        subtitle: String(it.status || it.execution_status || ''),
        raw: it
      })),
      async (it) => {
        if (forConfirm) {
          const itemId = it.id?.toString()
          if (!itemId) return
          const ok = await new Promise<boolean>((resolve) => {
            uni.showModal({
              title: '确认明细',
              content:
                String(it.status) === 'completed'
                  ? '确认该设备执行结果？'
                  : '结果未填完也可确认，将自动完成后再确认。是否继续？',
              success: (m) => resolve(!!m.confirm)
            })
          })
          if (!ok) return
          try {
            await http.post(`${cfg.value.executionBase}/item/${itemId}/confirm`, { client: 'mp' })
            uni.showToast({ title: '已确认', icon: 'success' })
          } catch (e: unknown) {
            uni.showToast({
              title: e instanceof Error ? e.message : '确认失败',
              icon: 'none'
            })
          }
          return
        }
        const execId = it.execution_id?.toString()
        const itemId = it.id?.toString()
        if (execId && itemId) openDetail(execId, itemId)
      }
    )
  } catch (e: unknown) {
    uni.hideLoading()
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  }
}

function levelFromTemplate(t: Record<string, unknown>) {
  return (
    String(t[cfg.value.levelField] || '') ||
    String(t.maintenance_level || '') ||
    String(t.inspection_type || '') ||
    String(t.pm_type || '') ||
    ''
  )
}

function applyTemplate(t: Record<string, unknown>) {
  adhoc.templateId = String(t.id || '')
  adhoc.templateName = String(t.template_name || t.id || '')
  const lv = levelFromTemplate(t)
  if (lv) adhoc.level = lv
  if (t.cycle_type) adhoc.cycleType = String(t.cycle_type)
  if (t.cycle_value != null && t.cycle_value !== '') adhoc.cycleValue = String(t.cycle_value)
}

function pickTemplate() {
  openPicker(
    '选择模板',
    adhoc.templates.map((t) => ({
      key: String(t.id || ''),
      title: String(t.template_name || t.id || ''),
      subtitle: levelFromTemplate(t) || undefined,
      raw: t
    })),
    (t) => applyTemplate(t)
  )
}

function onCycleTypeChange(e: { detail: { value: string } }) {
  const idx = Number(e.detail.value)
  adhoc.cycleType = cycleTypes[idx] || 'month'
}

function onPlannedDateChange(e: { detail: { value: string } }) {
  adhoc.plannedDate = e.detail.value || todayYmd()
}

function closeAdhoc() {
  adhoc.visible = false
  adhoc.saving = false
  adhoc.device = null
}

async function createAdHoc(device: Record<string, unknown>) {
  let templates: Record<string, unknown>[] = []
  try {
    const raw = await http.get<unknown>(cfg.value.templateListPath, { limit: 50 })
    const list = Array.isArray(raw) ? raw : (raw as { records?: unknown[] })?.records || []
    templates = list.map((e) => ({ ...(e as object) })) as Record<string, unknown>[]
  } catch {
    templates = []
  }
  if (!templates.length) {
    uni.showToast({ title: '暂无可用模板', icon: 'none' })
    return
  }
  adhoc.device = device
  adhoc.templates = templates
  adhoc.plannedDate = todayYmd()
  adhoc.cycleType = 'month'
  adhoc.cycleValue = '1'
  adhoc.level = ''
  applyTemplate(templates[0])
  adhoc.visible = true
}

async function submitAdhoc() {
  if (!adhoc.device || !adhoc.templateId) {
    uni.showToast({ title: '请选择模板', icon: 'none' })
    return
  }
  if (!adhoc.level.trim()) {
    uni.showToast({ title: `请填写${cfg.value.levelLabel}`, icon: 'none' })
    return
  }
  const cycleValue = Number(adhoc.cycleValue)
  const cycleDays = calcCycleDays(adhoc.cycleType, cycleValue)
  if (!Number.isFinite(cycleValue) || cycleValue <= 0 || cycleDays == null) {
    uni.showToast({ title: '请填写有效周期', icon: 'none' })
    return
  }
  const plannedDate = adhoc.plannedDate || todayYmd()
  adhoc.saving = true
  try {
    const body: Record<string, unknown> = {
      template_id: adhoc.templateId,
      template_name: adhoc.templateName,
      [cfg.value.levelField]: adhoc.level.trim(),
      device_id: adhoc.device.id,
      cycle_type: adhoc.cycleType,
      cycle_value: cycleValue,
      cycle_days: cycleDays,
      planned_date: plannedDate,
      execute_start_time: `${plannedDate} 00:00:00`,
      execute_end_time: `${plannedDate} 23:59:59`,
      client: 'mp'
    }
    const created = await http.post<Record<string, unknown>>(
      `${cfg.value.executionBase}/ad-hoc`,
      body
    )
    const execId = created?.id?.toString()
    if (!execId) throw new Error('创建失败')
    const detail = await http.get<Record<string, unknown>>(`${cfg.value.executionBase}/${execId}`)
    const items = Array.isArray(detail?.items) ? (detail.items as Record<string, unknown>[]) : []
    let itemId = items.find((it) => String(it.device_id) === String(adhoc.device?.id))?.id
    if (!itemId && items[0]) itemId = items[0].id
    closeAdhoc()
    if (itemId) openDetail(execId, String(itemId))
    loadDue()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '创建失败', icon: 'none' })
  } finally {
    adhoc.saving = false
  }
}

function openDetail(execId: string, itemId: string) {
  uni.navigateTo({
    url: `/pages/ops/detail?module=${moduleKey.value}&executionId=${execId}&itemId=${itemId}`
  })
}
</script>

<style scoped>
.hub {
  min-height: 100vh;
  padding: 24rpx 24rpx 48rpx;
  background: #f5f7fa;
}
.toolbar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 28rpx;
  flex-wrap: wrap;
}
.toolbar button {
  margin: 0;
}
.section-label {
  font-size: 24rpx;
  color: #98a2b3;
  margin: 0 8rpx 16rpx;
}
.empty {
  text-align: center;
  color: #98a2b3;
  padding: 48rpx;
  font-size: 26rpx;
}
.due-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  border: 1px solid rgba(26, 95, 180, 0.08);
}
.due-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2a37;
}
.due-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  max-height: 70vh;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx 28rpx 40rpx;
  box-sizing: border-box;
}
.adhoc-sheet {
  max-height: 85vh;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
  color: #1f2a37;
}
.sheet-list {
  max-height: 52vh;
  margin-bottom: 20rpx;
}
.sheet-row {
  padding: 22rpx 8rpx;
  border-bottom: 1px solid #eef2f6;
}
.sheet-row-title {
  display: block;
  font-size: 28rpx;
  color: #1f2a37;
}
.sheet-row-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #667085;
}
.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 18rpx 0;
  border-bottom: 1px solid #eef2f6;
}
.form-row .label {
  flex-shrink: 0;
  font-size: 28rpx;
  color: #475467;
}
.form-row .value,
.form-row .input {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: #1f2a37;
}
.hint {
  margin: 12rpx 0;
  font-size: 24rpx;
  color: #98a2b3;
}
.adhoc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 24rpx;
}
</style>
