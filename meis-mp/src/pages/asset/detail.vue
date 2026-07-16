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

const STATUS_MAP: Record<string, string> = {
  normal: '正常',
  maintenance: '维修中',
  pending_verify: '待验收',
  scrap: '报废',
  idle: '闲置'
}

function display(key: string) {
  if (!device.value) return '—'
  const v = device.value[key]
  if (v === null || v === undefined || v === '') return '—'
  if (key === 'device_status') return STATUS_MAP[String(v)] || String(v)
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

<style scoped>
.detail-page {
  padding: 24rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 8rpx 24rpx 32rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1px solid #f0f2f5;
  gap: 24rpx;
}
.label {
  color: #667085;
  flex-shrink: 0;
}
.value {
  text-align: right;
  color: #1f2a37;
  word-break: break-all;
}
.btn-repair {
  margin-top: 32rpx;
  background: #1a5fb4;
  color: #fff;
  border-radius: 12rpx;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: #98a2b3;
}
</style>
