<template>
  <view class="page">
    <view class="section">
      <text class="section-title">借出中借调单</text>
      <view class="row-actions">
        <button size="mini" type="primary" @click="pickLoan">选择借调单</button>
      </view>
      <view v-if="loan" class="device-box">
        <text class="d-name">{{ loan.loan_no }}</text>
        <text class="d-meta">{{ loan.device_name }} · {{ loan.device_code }}</text>
        <text v-if="loan.to_dept_name" class="d-meta">借入科室 {{ loan.to_dept_name }}</text>
      </view>
      <view v-else class="hint">请选择状态为「借出中」的借调单</view>
    </view>

    <view class="section">
      <text class="section-title">归还信息</text>
      <view class="field">
        <text class="label">归还日期</text>
        <picker mode="date" :value="returnDate" @change="onReturnDateChange">
          <text class="value">{{ returnDate || '请选择' }}</text>
        </picker>
      </view>
      <view class="field col">
        <text class="label">设备状况</text>
        <textarea
          v-model="conditionDesc"
          class="textarea"
          placeholder="可选：外观、功能等"
          maxlength="500"
        />
      </view>
    </view>

    <view class="actions">
      <button type="primary" :loading="saving" :disabled="!canSave" @click="submit">提交归还</button>
    </view>

    <view v-if="picker.visible" class="overlay" @click="closePicker">
      <view class="sheet" @click.stop>
        <view class="sheet-title">选择借出中单据</view>
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
          <view v-if="!picker.rows.length" class="empty">暂无借出中单据</view>
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
const loan = ref<Record<string, unknown> | null>(null)
const returnDate = ref('')
const conditionDesc = ref('')
const saving = ref(false)

type PickerRow = { key?: string; title: string; subtitle?: string; raw: Record<string, unknown> }
const picker = reactive({
  visible: false,
  rows: [] as PickerRow[]
})

const canSave = computed(() => !!(loan.value?.id && returnDate.value))

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

onLoad(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  returnDate.value = todayYmd()
})

function onReturnDateChange(e: { detail: { value: string } }) {
  returnDate.value = e.detail.value || todayYmd()
}

function closePicker() {
  picker.visible = false
  picker.rows = []
}

function onPick(idx: number) {
  const row = picker.rows[idx]
  closePicker()
  if (row) loan.value = row.raw
}

async function pickLoan() {
  uni.showLoading({ title: '加载中' })
  try {
    const data = await http.get<unknown>('/shared/loan/page', {
      page: 1,
      size: 50,
      status: 'on_loan'
    })
    const list = asRecords(data)
    picker.rows = list.map((r) => ({
      key: String(r.id || ''),
      title: String(r.loan_no || r.id || ''),
      subtitle: [r.device_name, r.device_code, r.to_dept_name].filter(Boolean).join(' · '),
      raw: r
    }))
    picker.visible = true
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function submit() {
  if (!canSave.value || !loan.value) {
    uni.showToast({ title: '请选择借调单与归还日期', icon: 'none' })
    return
  }
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '提交归还',
      content: `确认归还「${loan.value?.device_name || ''}」？`,
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  saving.value = true
  try {
    await http.post('/shared/return', {
      loan_id: loan.value.id,
      return_date: returnDate.value,
      condition_desc: conditionDesc.value,
      applicant_id: auth.user?.userId
    })
    uni.showToast({ title: '已提交', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '提交失败', icon: 'none' })
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
.hint {
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
