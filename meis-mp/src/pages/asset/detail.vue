<template>
  <view class="detail-page">
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!device" class="empty">设备不存在</view>
    <view v-else class="card">
      <view class="row" v-for="f in fields" :key="f.key">
        <text class="label">{{ f.label }}</text>
        <text class="value">{{ display(f.key) }}</text>
      </view>
      <button class="btn-repair" @click="goRepair">去报修</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { resolveStatusLabel } from '@/utils/statusLabels'

const auth = useAuthStore()
const deviceId = ref('')
const device = ref<Record<string, unknown> | null>(null)
const loading = ref(false)

const fields = [
  { key: 'device_code', label: '设备编码' },
  { key: 'device_name', label: '设备名称' },
  { key: 'brand', label: '品牌' },
  { key: 'model', label: '型号' },
  { key: 'specification', label: '规格' },
  { key: 'serial_number', label: '序列号' },
  { key: 'dept_name', label: '使用科室' },
  { key: 'device_status', label: '设备状态' },
  { key: 'location', label: '位置' }
]

function display(key: string) {
  if (!device.value) return '—'
  const v = device.value[key]
  if (v === null || v === undefined || v === '') return '—'
  if (key === 'device_status') return resolveStatusLabel('device_status', v)
  return String(v)
}

async function load() {
  if (!deviceId.value) return
  loading.value = true
  try {
    const data = await http.get<Record<string, unknown>>(`/asset/device/${deviceId.value}/detail`)
    device.value = data
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goRepair() {
  const code = device.value?.device_code
  if (!code) {
    uni.showToast({ title: '无设备编码', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/repair/scan?code=${encodeURIComponent(String(code))}` })
}

onLoad((query) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  deviceId.value = String(query?.id || '')
  load()
})
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.detail-page {
  padding: 24rpx;
  min-height: 100vh;
  background: $meis-page-bg;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: $meis-radius-lg;
  padding: 8rpx 24rpx 32rpx;
  border: 1px solid $meis-border;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1px solid $meis-border;
  gap: 24rpx;
}
.label {
  color: $meis-text-secondary;
  flex-shrink: 0;
}
.value {
  text-align: right;
  color: $meis-text;
  word-break: break-all;
}
.btn-repair {
  margin-top: 32rpx;
  background: $meis-primary;
  color: #fff;
  border-radius: $meis-radius;
  border: none;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: $meis-text-muted;
}
</style>
