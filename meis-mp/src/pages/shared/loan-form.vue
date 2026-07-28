<template>
  <view class="page">
    <view class="section">
      <text class="section-title">公用设备</text>
      <view v-if="editable" class="row-actions">
        <button size="mini" type="primary" @click="scanDevice">扫码选设备</button>
        <button size="mini" @click="searchDevice">关键词查找</button>
        <button size="mini" @click="pickFromList">从列表选</button>
      </view>
      <view v-if="device" class="device-box">
        <text class="d-name">{{ device.device_name }}</text>
        <text class="d-meta">
          {{ device.device_code }} · {{ device.dept_name || fromDeptName || '—' }}
        </text>
      </view>
      <view v-else class="hint">请扫码或选择公用设备</view>
    </view>

    <view class="section">
      <text class="section-title">借调信息</text>
      <view class="field" @click="editable && openDeptPicker()">
        <text class="label">借入科室</text>
        <text class="value">{{ toDeptName || '请选择' }}</text>
      </view>
      <view class="field">
        <text class="label">计划开始</text>
        <picker v-if="editable" mode="date" :value="loanStart" @change="onStartChange">
          <text class="value">{{ loanStart || '请选择' }}</text>
        </picker>
        <text v-else class="value">{{ loanStart || '—' }}</text>
      </view>
      <view class="field">
        <text class="label">计划结束</text>
        <picker v-if="editable" mode="date" :value="loanEnd" @change="onEndChange">
          <text class="value">{{ loanEnd || '请选择' }}</text>
        </picker>
        <text v-else class="value">{{ loanEnd || '—' }}</text>
      </view>
      <view class="field col">
        <text class="label">借调原因</text>
        <textarea
          v-model="reason"
          class="textarea"
          :disabled="!editable"
          placeholder="请填写借调原因"
          maxlength="500"
        />
      </view>
      <view v-if="loanNo" class="field">
        <text class="label">单号 / 状态</text>
        <text class="value">{{ loanNo }} · {{ statusLabel(status) }}</text>
      </view>
    </view>

    <view v-if="editable" class="actions">
      <button type="primary" :loading="saving" :disabled="!canSave" @click="save(false)">
        保存草稿
      </button>
      <button
        v-if="!loanId || status === 'draft'"
        :loading="saving"
        :disabled="!canSave"
        @click="save(true)"
      >
        保存并提交
      </button>
    </view>
    <view v-else class="hint-ro">当前状态不可编辑</view>

    <!-- 选择器 -->
    <view v-if="picker.visible" class="overlay" @click="closePicker">
      <view class="sheet" @click.stop>
        <view class="sheet-title">{{ picker.title }}</view>
        <scroll-view scroll-y class="sheet-list">
          <view
            v-for="(row, idx) in picker.rows"
            :key="String(row.key || idx)"
            class="sheet-row"
            @click="onPick(idx)"
          >
            <text class="sheet-row-title">{{ row.title }}</text>
            <text v-if="row.subtitle" class="sheet-row-sub">{{ row.subtitle }}</text>
          </view>
          <view v-if="!picker.rows.length" class="empty">暂无数据</view>
        </scroll-view>
        <button size="mini" @click="closePicker">取消</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loanId = ref<string | null>(null)
const loanNo = ref('')
const status = ref('draft')
const device = ref<Record<string, unknown> | null>(null)
const fromDeptName = ref('')
const toDeptId = ref('')
const toDeptName = ref('')
const loanStart = ref('')
const loanEnd = ref('')
const reason = ref('')
const saving = ref(false)
const depts = ref<Record<string, unknown>[]>([])

const LOAN_STATUS: Record<string, string> = {
  draft: '草稿',
  pending: '待审核',
  approved: '已批准',
  on_loan: '借出中',
  returned: '已归还',
  rejected: '已驳回'
}

type PickerRow = { key?: string; title: string; subtitle?: string; raw: Record<string, unknown> }
const picker = reactive({
  visible: false,
  title: '',
  rows: [] as PickerRow[]
})
let pickerSelect: ((row: Record<string, unknown>) => void) | null = null

const editable = computed(() => ['draft', 'pending'].includes(status.value) || !loanId.value)
const canSave = computed(() => !!(device.value?.id && toDeptId.value && loanStart.value && loanEnd.value))

function statusLabel(s: unknown) {
  const k = String(s || '')
  return LOAN_STATUS[k] || k || '—'
}

function todayYmd() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function asRecords(data: unknown): Record<string, unknown>[] {
  if (Array.isArray(data)) return data as Record<string, unknown>[]
  if (data && typeof data === 'object' && Array.isArray((data as { records?: unknown[] }).records)) {
    return (data as { records: Record<string, unknown>[] }).records
  }
  return []
}

onLoad(async (query) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loanStart.value = todayYmd()
  loanEnd.value = todayYmd()
  await loadDepts()
  if (query?.id) {
    await loadLoan(String(query.id))
  }
})

async function loadDepts() {
  try {
    const raw = await http.get<unknown>('/system/departments')
    depts.value = asRecords(raw)
  } catch {
    depts.value = []
  }
}

async function loadLoan(id: string) {
  try {
    uni.showLoading({ title: '加载中' })
    const data = await http.get<Record<string, unknown>>(`/shared/loan/${id}`)
    loanId.value = String(data.id || id)
    loanNo.value = String(data.loan_no || '')
    status.value = String(data.status || 'draft')
    toDeptId.value = String(data.to_dept_id || '')
    toDeptName.value = String(data.to_dept_name || '')
    fromDeptName.value = String(data.from_dept_name || '')
    loanStart.value = String(data.loan_start || '').slice(0, 10)
    loanEnd.value = String(data.loan_end || '').slice(0, 10)
    reason.value = String(data.reason || '')
    if (data.device_id) {
      device.value = {
        id: data.device_id,
        device_code: data.device_code,
        device_name: data.device_name,
        dept_id: data.from_dept_id,
        dept_name: data.from_dept_name
      }
    }
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

function selectDevice(d: Record<string, unknown>) {
  device.value = d
  fromDeptName.value = String(d.dept_name || '')
}

function openPicker(title: string, rows: PickerRow[], onSelect: (row: Record<string, unknown>) => void) {
  picker.title = title
  picker.rows = rows
  pickerSelect = onSelect
  picker.visible = true
}

function closePicker() {
  picker.visible = false
  picker.rows = []
  pickerSelect = null
}

function onPick(idx: number) {
  const row = picker.rows[idx]
  const cb = pickerSelect
  closePicker()
  if (row && cb) cb(row.raw)
}

function scanDevice() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const code = (res.result || '').trim()
      if (!code) {
        uni.showToast({ title: '未识别到内容', icon: 'none' })
        return
      }
      void lookupDevices(code, true)
    },
    fail: () => uni.showToast({ title: '扫码取消或失败', icon: 'none' })
  })
}

function searchDevice() {
  uni.showModal({
    title: '查找公用设备',
    editable: true,
    placeholderText: '编码 / 名称',
    success: (res) => {
      if (res.confirm && res.content) void lookupDevices(res.content.trim(), false)
    }
  })
}

async function pickFromList() {
  await lookupDevices('', false)
}

async function lookupDevices(keyword: string, exactPrefer: boolean) {
  uni.showLoading({ title: '查找设备' })
  try {
    const data = await http.get<unknown>('/shared/device/page', {
      page: 1,
      size: 50,
      ...(keyword ? { keyword } : {})
    })
    let list = asRecords(data)
    if (!list.length) {
      uni.showToast({ title: '未找到公用设备', icon: 'none' })
      return
    }
    if (exactPrefer && keyword) {
      const hit = list.find(
        (d) =>
          String(d.device_code || '') === keyword ||
          String(d.id || '') === keyword
      )
      if (hit) {
        selectDevice(hit)
        uni.showToast({ title: '已选中', icon: 'success' })
        return
      }
    }
    if (list.length === 1) {
      selectDevice(list[0])
      return
    }
    openPicker(
      '选择公用设备',
      list.map((d) => ({
        key: String(d.id || ''),
        title: String(d.device_name || d.device_code || '设备'),
        subtitle: [d.device_code, d.dept_name, d.shared_loan_status].filter(Boolean).join(' · '),
        raw: d
      })),
      (d) => selectDevice(d)
    )
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '查找失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

function onStartChange(e: { detail: { value: string } }) {
  loanStart.value = e.detail.value || todayYmd()
}

function onEndChange(e: { detail: { value: string } }) {
  loanEnd.value = e.detail.value || todayYmd()
}

function openDeptPicker() {
  if (!depts.value.length) {
    uni.showToast({ title: '暂无科室', icon: 'none' })
    return
  }
  openPicker(
    '选择借入科室',
    depts.value.map((d) => ({
      key: String(d.id || ''),
      title: String(d.dept_name || d.dept_code || ''),
      subtitle: String(d.dept_code || ''),
      raw: d
    })),
    (d) => {
      toDeptId.value = String(d.id || '')
      toDeptName.value = String(d.dept_name || '')
    }
  )
}

async function save(andSubmit: boolean) {
  if (!canSave.value || !device.value) {
    uni.showToast({ title: '请完善设备、科室与日期', icon: 'none' })
    return
  }
  if (loanStart.value > loanEnd.value) {
    uni.showToast({ title: '结束日期不能早于开始', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      device_id: device.value.id,
      device_code: device.value.device_code,
      device_name: device.value.device_name,
      to_dept_id: toDeptId.value,
      loan_start: loanStart.value,
      loan_end: loanEnd.value,
      reason: reason.value,
      applicant_id: auth.user?.userId,
      status: status.value || 'draft'
    }
    if (loanId.value) body.id = loanId.value
    const saved = await http.post<Record<string, unknown>>('/shared/loan', body)
    loanId.value = String(saved?.id || loanId.value)
    loanNo.value = String(saved?.loan_no || loanNo.value)
    status.value = String(saved?.status || status.value || 'draft')
    if (andSubmit && loanId.value) {
      await http.post(`/shared/loan/${loanId.value}/submit`, { applicantId: auth.user?.userId })
      uni.showToast({ title: '已提交', icon: 'success' })
    } else {
      uni.showToast({ title: '已保存', icon: 'success' })
    }
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx 24rpx 80rpx;
  background: $meis-page-bg;
  box-sizing: border-box;
}
.section {
  background: #fff;
  border-radius: $meis-radius-lg;
  padding: 24rpx;
  margin-bottom: 16rpx;
  border: 1px solid $meis-border;
}
.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: $meis-text;
  margin-bottom: 16rpx;
}
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 16rpx;
}
.row-actions button {
  margin: 0;
}
.device-box {
  padding: 16rpx;
  background: $meis-page-bg;
  border-radius: $meis-radius;
}
.d-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $meis-text;
}
.d-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}
.hint,
.hint-ro {
  font-size: 24rpx;
  color: $meis-text-muted;
  padding: 12rpx 0;
}
.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 18rpx 0;
  border-bottom: 1px solid $meis-border;
}
.field.col {
  flex-direction: column;
  align-items: stretch;
}
.field .label {
  flex-shrink: 0;
  font-size: 28rpx;
  color: $meis-text-secondary;
}
.field .value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: $meis-text;
}
.textarea {
  margin-top: 12rpx;
  width: 100%;
  min-height: 140rpx;
  font-size: 28rpx;
  color: $meis-text;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 24rpx;
}
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  max-height: 70vh;
  background: #fff;
  border-radius: 20rpx 20rpx 0 0;
  padding: 28rpx 28rpx 40rpx;
  box-sizing: border-box;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
  color: $meis-text;
}
.sheet-list {
  max-height: 52vh;
  margin-bottom: 20rpx;
}
.sheet-row {
  padding: 22rpx 8rpx;
  border-bottom: 1px solid $meis-border;
}
.sheet-row-title {
  display: block;
  font-size: 28rpx;
  color: $meis-text;
}
.sheet-row-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}
.empty {
  text-align: center;
  padding: 40rpx;
  color: $meis-text-muted;
}
</style>
