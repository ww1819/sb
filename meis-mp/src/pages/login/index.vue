<template>
  <view class="login-page">
    <view class="hero">
      <text class="brand">MEIS</text>
      <text class="subtitle">医疗设备助手</text>
    </view>
    <view class="form-card">
      <view class="field">
        <text class="label">医院编码</text>
        <input v-model="tenantCode" class="input" placeholder="如 demo" />
      </view>
      <view class="field">
        <text class="label">用户名</text>
        <input v-model="username" class="input" placeholder="请输入用户名" />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="password" class="input" password placeholder="请输入密码" />
      </view>
      <button class="btn-primary" :loading="loading" @click="onLogin">登录</button>
      <text class="hint">小程序仅通过公网网关访问，无需选择内外网</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { requestSubscribe } from '@/utils/subscribe'

const auth = useAuthStore()
const tenantCode = ref('demo')
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)

onShow(() => {
  auth.restore()
  if (auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/home/index' })
  }
})

async function onLogin() {
  if (!tenantCode.value.trim() || !username.value.trim() || !password.value) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await auth.login(tenantCode.value.trim(), username.value.trim(), password.value)
    await requestSubscribe('login')
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/home/index' })
    }, 300)
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.login-page {
  min-height: 100vh;
  padding: 120rpx 48rpx 48rpx;
  background: linear-gradient(180deg, $meis-page-bg-top 0%, $meis-page-bg 45%);
  box-sizing: border-box;
}
.hero {
  margin-bottom: 56rpx;
}
.brand {
  display: block;
  font-size: 64rpx;
  font-weight: 700;
  color: $meis-primary;
  letter-spacing: 4rpx;
}
.subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  color: $meis-text-secondary;
}
.form-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 40rpx 32rpx;
  border: 1px solid $meis-border;
}
.field {
  margin-bottom: 28rpx;
}
.label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  color: $meis-text-secondary;
}
.input {
  height: 80rpx;
  padding: 0 24rpx;
  background: $meis-page-bg;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.btn-primary {
  margin-top: 16rpx;
  background: $meis-primary;
  color: #fff;
  border-radius: 12rpx;
  font-size: 30rpx;
  border: none;
}
.hint {
  display: block;
  margin-top: 24rpx;
  text-align: center;
  font-size: 22rpx;
  color: $meis-text-muted;
}
</style>
