<template>
  <view class="detail-page">
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!device" class="empty">设备不存在</view>
    <view v-else>
      <view class="card">
        <view class="section-title">基本信息</view>
        <view class="row" v-for="f in basicFields" :key="f.key">
          <text class="label">{{ f.label }}</text>
          <text class="value">{{ display(f) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="section-title">标识与责任</view>
        <view class="row" v-for="f in idFields" :key="f.key">
          <text class="label">{{ f.label }}</text>
          <text class="value">{{ display(f) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="section-title">证照（只读）</view>
        <view v-if="licenses.length === 0" class="sub-empty">暂无证照</view>
        <view v-for="(row, i) in licenses" :key="row.id || i" class="sub-row">
          <text class="sub-main">{{ resolveStatusLabel('device_license_type', row.license_type) }} · {{ text(row.license_no) }}</text>
          <text class="sub-meta">有效期 {{ text(row.expiry_date) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="section-title">培训授权（只读）</view>
        <view v-if="trainings.length === 0" class="sub-empty">暂无授权记录</view>
        <view v-for="(row, i) in trainings" :key="row.id || i" class="sub-row">
          <text class="sub-main">{{ text(row.user_name) }} · {{ resolveStatusLabel('auth_scope', row.auth_scope) }}</text>
          <text class="sub-meta">有效期至 {{ text(row.expiry_date) }}</text>
        </view>
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

type Field = { key: string; label: string; dictType?: string }

const auth = useAuthStore()
const deviceId = ref('')
const device = ref<Record<string, unknown> | null>(null)
const licenses = ref<Record<string, unknown>[]>([])
const trainings = ref<Record<string, unknown>[]>([])
const loading = ref(false)

const basicFields: Field[] = [
  { key: 'device_code', label: '设备编码' },
  { key: 'device_name', label: '设备名称' },
  { key: 'brand', label: '品牌' },
  { key: 'model', label: '型号' },
  { key: 'specification', label: '规格' },
  { key: 'serial_number', label: '序列号' },
  { key: 'dept_name', label: '使用科室' },
  { key: 'device_status', label: '设备状态', dictType: 'device_status' },
  { key: 'location', label: '位置' }
]

const idFields: Field[] = [
  { key: 'udi_di', label: 'UDI-DI' },
  { key: 'udi_pi', label: 'UDI-PI' },
  { key: 'lot_no', label: '生产批号' },
  { key: 'asset_manager_name', label: '资产责任人' },
  { key: 'clinical_owner_name', label: '临床责任人' },
  { key: 'eq_class', label: '器械类别', dictType: 'eq_class' },
  { key: 'criticality', label: '关键等级', dictType: 'device_criticality' },
  { key: 'acquisition_mode', label: '购置方式', dictType: 'acquisition_mode' },
  { key: 'depreciation_method', label: '折旧方法', dictType: 'depreciation_method' },
  { key: 'ip_address', label: 'IP' },
  { key: 'mac_address', label: 'MAC' }
]

function text(v: unknown) {
  if (v === null || v === undefined || v === '') return '—'
  return String(v)
}

function display(f: Field) {
  if (!device.value) return '—'
  const v = device.value[f.key]
  if (v === null || v === undefined || v === '') return '—'
  if (f.dictType) return resolveStatusLabel(f.dictType, v)
  return String(v)
}

async function loadChildren(id: string) {
  try {
    const [lic, tr] = await Promise.all([
      http.get<Record<string, unknown>[]>(`/asset/device-license/by-device/${id}`),
      http.get<Record<string, unknown>[]>(`/asset/device-training-auth/by-device/${id}`)
    ])
    licenses.value = Array.isArray(lic) ? lic : []
    trainings.value = Array.isArray(tr) ? tr : []
  } catch {
    licenses.value = []
    trainings.value = []
  }
}

async function load() {
  if (!deviceId.value) return
  loading.value = true
  try {
    const data = await http.get<Record<string, unknown>>(`/asset/device/${deviceId.value}/detail`)
    device.value = data
    await loadChildren(deviceId.value)
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
  padding: 8rpx 24rpx 24rpx;
  border: 1px solid $meis-border;
  margin-bottom: 20rpx;
}
.section-title {
  padding: 20rpx 0 8rpx;
  font-weight: 600;
  color: $meis-text;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 0;
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
.sub-empty {
  color: $meis-text-secondary;
  padding: 16rpx 0 8rpx;
  font-size: 26rpx;
}
.sub-row {
  padding: 16rpx 0;
  border-bottom: 1px solid $meis-border;
}
.sub-main {
  display: block;
  color: $meis-text;
  font-size: 28rpx;
}
.sub-meta {
  display: block;
  margin-top: 6rpx;
  color: $meis-text-secondary;
  font-size: 24rpx;
}
.btn-repair {
  margin-top: 8rpx;
  margin-bottom: 40rpx;
  background: $meis-primary;
  color: #fff;
  border-radius: $meis-radius;
  border: none;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: $meis-text-secondary;
}
</style>
