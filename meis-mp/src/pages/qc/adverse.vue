<template>
  <view class="page">
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
        <text class="d-meta">{{ device.device_code }} · {{ device.dept_name || '—' }}</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">事件信息</text>
      <view class="field">
        <text class="label">事件类型</text>
        <input v-model="eventType" class="input" placeholder="如：故障/伤害/近失" />
      </view>
      <view class="field">
        <text class="label">严重程度</text>
        <picker :range="severityLabels" :value="severityIndex" @change="onSeverityChange">
          <view class="picker">{{ severityLabels[severityIndex] }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">事件描述 *</text>
        <textarea
          v-model="description"
          class="textarea"
          placeholder="简要描述发生经过与后果"
          maxlength="800"
        />
      </view>
      <view class="field">
        <text class="label">备注</text>
        <input v-model="remark" class="input" placeholder="可选" />
      </view>
    </view>

    <button class="submit" type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
      提交上报
    </button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

interface DeviceInfo {
  id: string
  device_code?: string
  device_name?: string
  dept_id?: string
  dept_name?: string
}

const auth = useAuthStore()
const deviceCode = ref('')
const device = ref<DeviceInfo | null>(null)
const eventType = ref('')
const description = ref('')
const remark = ref('')
const submitting = ref(false)

const severityOptions = [
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'critical', label: '危急' }
]
const severityLabels = severityOptions.map((o) => o.label)
const severityIndex = ref(1)

const canSubmit = computed(
  () => !!device.value?.id && description.value.trim().length > 0 && !submitting.value
)

onLoad((q) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  const code = q?.code ? String(q.code) : ''
  if (code) {
    deviceCode.value = code
    lookupByCode(code)
  }
})

function onSeverityChange(e: { detail: { value: string } }) {
  severityIndex.value = Number(e.detail.value) || 0
}

function scan() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const code = (res.result || '').trim()
      if (!code) {
        uni.showToast({ title: '未识别到编码', icon: 'none' })
        return
      }
      deviceCode.value = code
      lookupByCode(code)
    },
    fail: () => uni.showToast({ title: '扫码取消或失败', icon: 'none' })
  })
}

function lookupManual() {
  const code = deviceCode.value.trim()
  if (!code) {
    uni.showToast({ title: '请输入设备编码', icon: 'none' })
    return
  }
  lookupByCode(code)
}

async function lookupByCode(code: string) {
  try {
    uni.showLoading({ title: '查询中' })
    const data = await http.get<DeviceInfo>(`/asset/device/by-code/${encodeURIComponent(code)}`)
    device.value = data
    deviceCode.value = data.device_code || code
  } catch (e: unknown) {
    device.value = null
    const msg = e instanceof Error ? e.message : '设备不存在'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function submit() {
  if (!canSubmit.value || !device.value) return
  submitting.value = true
  try {
    await http.post('/qc/adverse', {
      device_id: device.value.id,
      device_code: device.value.device_code,
      device_name: device.value.device_name,
      event_type: eventType.value.trim() || null,
      severity_level: severityOptions[severityIndex.value]?.value || 'medium',
      event_description: description.value.trim(),
      remark: remark.value.trim() || null,
      reporter_id: auth.user?.userId
    })
    uni.showToast({ title: '已上报', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '上报失败'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 28rpx 80rpx;
  background: #f5f7fa;
  box-sizing: border-box;
}
.section {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2a37;
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
  font-size: 24rpx;
  color: #667085;
  margin-bottom: 8rpx;
}
.input,
.picker,
.textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 16rpx 20rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.textarea {
  min-height: 160rpx;
}
.device-box {
  padding: 20rpx;
  background: #eef4fb;
  border-radius: 12rpx;
}
.d-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2a37;
}
.d-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.submit {
  margin-top: 12rpx;
}
</style>
