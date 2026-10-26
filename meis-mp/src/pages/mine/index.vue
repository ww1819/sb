<template>
  <view class="mine-page">
    <view class="profile" @click="noop">
      <view class="avatar">{{ avatarChar }}</view>
      <view class="profile-body">
        <text class="name">{{ auth.displayName || '用户' }}</text>
        <text class="meta" v-if="metaLine">{{ metaLine }}</text>
      </view>
    </view>

    <view class="group">
      <view class="cell" hover-class="cell--pressed" :hover-stay-time="80" @click="onAbout">
        <text class="cell-label">关于 MEIS</text>
        <text class="cell-arrow">›</text>
      </view>
    </view>

    <view class="group">
      <view class="cell cell--danger" hover-class="cell--pressed" :hover-stay-time="80" @click="onLogout">
        <text class="cell-label cell-label--danger">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const avatarChar = computed(() => {
  const n = auth.displayName || '用'
  return n.slice(0, 1)
})

const metaLine = computed(() => {
  const u = auth.user
  if (!u) return ''
  const parts: string[] = []
  if (u.username) parts.push(`账号: ${u.username}`)
  if (u.tenantCode) parts.push(`租户: ${u.tenantCode}`)
  return parts.join('  ')
})

function noop() {}

function onAbout() {
  uni.showModal({
    title: '关于 MEIS',
    content: '医疗设备助手小程序：与 App/Web 同 API、同权限；在线扫码查询、报修与运维执行。',
    showCancel: false
  })
}

function onLogout() {
  uni.showModal({
    title: '确认退出',
    content: '退出后需要重新登录',
    success: (res) => {
      if (res.confirm) auth.logout()
    }
  })
}

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.mine-page {
  min-height: 100vh;
  background: $meis-page-bg;
  padding: 24rpx 0 48rpx;
  box-sizing: border-box;
}
.profile {
  display: flex;
  align-items: center;
  gap: 28rpx;
  margin: 16rpx 24rpx 24rpx;
  padding: 36rpx 28rpx;
  background: #fff;
  border-radius: 16rpx;
}
.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 20rpx;
  background: rgba(22, 119, 255, 0.12);
  color: $meis-primary;
  font-size: 48rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-body {
  flex: 1;
  min-width: 0;
}
.name {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: $meis-text;
}
.meta {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}
.group {
  margin: 0 24rpx 20rpx;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
}
.cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 28rpx;
}
.cell--pressed {
  background: rgba(0, 0, 0, 0.03);
}
.cell--danger {
  justify-content: center;
}
.cell-label {
  font-size: 30rpx;
  color: $meis-text;
}
.cell-label--danger {
  color: #ff4d4f;
}
.cell-arrow {
  font-size: 36rpx;
  color: $meis-text-muted;
}
</style>
