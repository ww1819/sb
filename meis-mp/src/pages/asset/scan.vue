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

function scan() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const raw = (res.result || '').trim()
      if (!raw) {
        uni.showToast({ title: '未识别到内容', icon: 'none' })
        return
      }
      code.value = raw
      openByCode(raw)
    },
    fail: () => uni.showToast({ title: '扫码取消或失败', icon: 'none' })
  })
}
</script>

<style scoped>
.scan-page {
  min-height: 100vh;
  padding: 32rpx;
  background: #f5f7fa;
}
.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 40rpx 32rpx;
  box-shadow: 0 8rpx 28rpx rgba(26, 95, 180, 0.06);
}
.title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: #1f2a37;
}
.desc {
  display: block;
  margin: 12rpx 0 32rpx;
  font-size: 24rpx;
  color: #667085;
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
  background: #f3f6fa;
  border-radius: 12rpx;
  padding: 22rpx 24rpx;
  font-size: 28rpx;
}
</style>
