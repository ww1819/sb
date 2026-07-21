<template>
  <view class="repair-page">
    <view class="section">
      <text class="section-title">设备</text>
      <view class="row-actions">
        <button class="btn" type="primary" size="mini" @click="scan">扫一扫</button>
        <button class="btn" size="mini" @click="lookupManual">按编码查询</button>
      </view>
      <view class="field">
        <text class="label">设备编码</text>
        <input v-model="deviceCode" class="input" placeholder="扫码或手输" @confirm="lookupManual" />
      </view>
      <view v-if="device" class="device-box">
        <text class="d-name">{{ device.device_name }}</text>
        <text class="d-meta">{{ device.dept_name || '—' }} · {{ statusLabel(device.device_status) }}</text>
        <text v-if="device.can_report === false" class="warn">{{ device.cannot_report_reason || '当前不可报修' }}</text>
        <text v-else class="ok">可报修</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">故障信息</text>
      <view class="field">
        <text class="label">紧急程度</text>
        <picker :range="urgencyLabels" :value="urgencyIndex" @change="onUrgencyChange">
          <view class="picker">{{ urgencyLabels[urgencyIndex] }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">故障描述</text>
        <textarea
          v-model="faultDescription"
          class="textarea"
          placeholder="请描述故障现象"
          maxlength="500"
        />
      </view>
    </view>

    <button
      class="submit"
      type="primary"
      :loading="submitting"
      :disabled="!canSubmit"
      @click="submit"
    >
      提交报修
    </button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { requestSubscribe } from '@/utils/subscribe'

interface DeviceInfo {
  id: string
  device_code?: string
  device_name?: string
  dept_id?: string
  dept_name?: string
  device_status?: string
  can_report?: boolean
  cannot_report_reason?: string
}

const auth = useAuthStore()
const deviceCode = ref('')
const device = ref<DeviceInfo | null>(null)
const faultDescription = ref('')
const submitting = ref(false)
const looking = ref(false)

const urgencyOptions = [
  { value: 'normal', label: '一般' },
  { value: 'urgent', label: '紧急' },
  { value: 'critical', label: '特急' }
]
const urgencyLabels = urgencyOptions.map((o) => o.label)
const urgencyIndex = ref(0)

const STATUS_MAP: Record<string, string> = {
  normal: '正常',
  maintenance: '维修中',
  pending_verify: '待验收',
  scrap: '报废',
  idle: '闲置'
}

function statusLabel(s?: string) {
  if (!s) return '—'
  return STATUS_MAP[s] || s
}

function onUrgencyChange(e: { detail: { value: string } }) {
  urgencyIndex.value = Number(e.detail.value)
}

const canSubmit = computed(() => {
  return !!device.value?.id
    && device.value.can_report !== false
    && !!faultDescription.value.trim()
    && !submitting.value
})

async function fetchByCode(code: string) {
  const c = code.trim()
  if (!c) {
    uni.showToast({ title: '请输入设备编码', icon: 'none' })
    return
  }
  looking.value = true
  try {
    const data = await http.get<DeviceInfo>(`/asset/device/by-code/${encodeURIComponent(c)}`)
    device.value = data
    deviceCode.value = String(data.device_code || c)
    if (data.can_report === false) {
      uni.showToast({ title: data.cannot_report_reason || '不可报修', icon: 'none' })
    }
  } catch (e: unknown) {
    device.value = null
    uni.showToast({ title: e instanceof Error ? e.message : '查找失败', icon: 'none' })
  } finally {
    looking.value = false
  }
}

function lookupManual() {
  fetchByCode(deviceCode.value)
}

function scan() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const raw = (res.result || '').trim()
      if (!raw) {
        uni.showToast({ title: '未识别到内容', icon: 'none' })
        return
      }
      deviceCode.value = raw
      fetchByCode(raw)
    },
    fail: () => {
      uni.showToast({ title: '扫码取消或失败', icon: 'none' })
    }
  })
}

function formatNow() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function submit() {
  if (!canSubmit.value || !device.value) return
  submitting.value = true
  try {
    const body = {
      device_id: device.value.id,
      device_code: device.value.device_code,
      device_name: device.value.device_name,
      reporter_id: auth.user?.userId,
      report_dept_id: device.value.dept_id,
      report_method: 'miniprogram',
      report_time: formatNow(),
      fault_description: faultDescription.value.trim(),
      urgency_level: urgencyOptions[urgencyIndex.value].value
    }
    const draft = await http.post<{ id: string }>('/repair/workorder', body)
    if (!draft?.id) throw new Error('创建报修单失败')
    const done = await http.post<{ wo_no?: string }>(`/repair/workorder/${draft.id}/submit`)
    await requestSubscribe('repair_submit')
    uni.showModal({
      title: '报修成功',
      content: `工单号：${done?.wo_no || draft.id}`,
      showCancel: false,
      success: () => {
        faultDescription.value = ''
        device.value = null
        deviceCode.value = ''
        uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/home/index' }) })
      }
    })
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((query) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  const code = query?.code ? decodeURIComponent(String(query.code)) : ''
  if (code) {
    deviceCode.value = code
    fetchByCode(code)
  }
})
</script>

<style scoped>
.repair-page {
  padding: 24rpx 24rpx 60rpx;
}
.section {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
}
.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.row-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.btn {
  margin: 0;
}
.field {
  margin-bottom: 20rpx;
}
.label {
  display: block;
  margin-bottom: 10rpx;
  font-size: 24rpx;
  color: #667085;
}
.input,
.picker,
.textarea {
  width: 100%;
  box-sizing: border-box;
  background: #f3f6fa;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
}
.textarea {
  min-height: 180rpx;
  width: auto;
}
.device-box {
  margin-top: 8rpx;
  padding: 20rpx;
  background: #f7fbff;
  border-radius: 12rpx;
  border: 1px solid #d6e6f8;
}
.d-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}
.d-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.warn {
  display: block;
  margin-top: 10rpx;
  color: #d92d20;
  font-size: 24rpx;
}
.ok {
  display: block;
  margin-top: 10rpx;
  color: #039855;
  font-size: 24rpx;
}
.submit {
  background: #1a5fb4;
  color: #fff;
  border-radius: 12rpx;
}
.submit[disabled] {
  opacity: 0.5;
}
</style>
