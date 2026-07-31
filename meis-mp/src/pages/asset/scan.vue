<template>
  <view class="scan-page">
    <view class="card">
      <text class="title">扫码查询台账</text>
      <text class="desc">扫描设备标签二维码，或手输设备编码查看详情</text>
      <view class="actions">
        <button type="primary" @click="scan">扫一扫</button>
        <button @click="lookup">按编码查询</button>
      </view>
      <input v-model="code" class="input" placeholder="设备编码" confirm-type="search" @confirm="lookup" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { scanBarcode } from '@/utils/scanCode'

const auth = useAuthStore()
const code = ref('')

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) uni.reLaunch({ url: '/pages/login/index' })
})

async function openByCode(raw: string) {
  const c = raw.trim()
  if (!c) {
    uni.showToast({ title: '请输入设备编码', icon: 'none' })
    return
  }
  uni.showLoading({ title: '查询中' })
  try {
    const data = await http.get<{ id: string }>(`/asset/device/by-code/${encodeURIComponent(c)}`)
    if (!data?.id) throw new Error('未找到设备')
    uni.navigateTo({ url: `/pages/asset/detail?id=${data.id}` })
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '查询失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

function lookup() {
  openByCode(code.value)
}

async function scan() {
  const raw = await scanBarcode()
  if (!raw) return
  code.value = raw
  openByCode(raw)
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.scan-page {
  min-height: 100vh;
  padding: 32rpx;
  background: $meis-page-bg;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: $meis-radius-lg;
  padding: 40rpx 32rpx;
  border: 1px solid $meis-border;
}
.title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: $meis-text;
}
.desc {
  display: block;
  margin: 12rpx 0 32rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
  line-height: 1.5;
}
.actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.actions button {
  flex: 1;
  margin: 0;
}
.input {
  background: $meis-page-bg;
  border-radius: $meis-radius;
  padding: 22rpx 24rpx;
  font-size: 28rpx;
  color: $meis-text;
}
</style>
