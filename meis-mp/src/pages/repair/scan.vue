<template>
  <view class="repair-page">
    <view class="section">
      <text class="section-title">设备</text>
      <view class="row-actions">
        <button class="btn" type="primary" size="mini" @click="scan">扫一扫</button>
        <button class="btn" size="mini" @click="lookupManual">搜索设备</button>
      </view>
      <view class="field">
        <text class="label">设备（编码/名称/首拼）</text>
        <input v-model="deviceCode" class="input" placeholder="扫码或输入名称/首拼/编码" @confirm="lookupManual" />
      </view>
      <view v-if="candidates.length" class="candidates">
        <text class="section-title">检索到 {{ candidates.length }} 台，请选择</text>
        <view
          v-for="(c, idx) in candidates"
          :key="String(c.id || idx)"
          class="candidate"
          @click="selectDevice(c)"
        >
          <text class="d-name">{{ c.device_name }}</text>
          <text class="d-meta">{{ c.device_code }} · {{ c.dept_name || '—' }} · {{ c.pinyin_code || '' }}</text>
        </view>
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
  pinyin_code?: string
  dept_id?: string
  dept_name?: string
  device_status?: string
  can_report?: boolean
  cannot_report_reason?: string
}

const auth = useAuthStore()
const deviceCode = ref('')
const device = ref<DeviceInfo | null>(null)
const candidates = ref<DeviceInfo[]>([])
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

async function fetchByQuery(code: string) {
  const c = code.trim()
  if (!c) {
    uni.showToast({ title: '请输入设备编码、名称或首拼', icon: 'none' })
    return
  }
  looking.value = true
  candidates.value = []
  device.value = null
  try {
    const list = await http.get<DeviceInfo[]>('/repair/workorder/devices/lookup', { q: c })
    const rows = Array.isArray(list) ? list : []
    if (!rows.length) {
      uni.showToast({ title: '没有检索到匹配设备', icon: 'none' })
      return
    }
    if (rows.length === 1) {
      selectDevice(rows[0])
      return
    }
    candidates.value = rows
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '查找失败', icon: 'none' })
  } finally {
    looking.value = false
  }
}

function selectDevice(row: DeviceInfo) {
  device.value = row
  candidates.value = []
  deviceCode.value = String(row.device_code || deviceCode.value)
  if (row.can_report === false) {
    uni.showToast({ title: row.cannot_report_reason || '不可报修', icon: 'none' })
  }
}

function lookupManual() {
  fetchByQuery(deviceCode.value)
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
      fetchByQuery(raw)
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
        candidates.value = []
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
    fetchByQuery(code)
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
.candidates {
  margin-top: 12rpx;
}
.candidate {
  padding: 20rpx;
  margin-bottom: 12rpx;
  background: #f7fbff;
  border-radius: 12rpx;
  border: 1px solid #d6e6f8;
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
