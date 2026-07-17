<template>
  <view class="home">
    <view class="hero">
      <view class="hero-top">
        <view>
          <text class="brand">MEIS</text>
          <text class="brand-sub">医疗设备助手</text>
        </view>
        <text class="logout" @click="onLogout">退出</text>
      </view>
      <view class="greet">
        <text class="greet-hi">你好，{{ auth.displayName || '用户' }}</text>
        <text class="greet-meta" v-if="auth.user?.tenantCode">
          {{ auth.user.tenantCode }} · 现场报修与资产查询
        </text>
      </view>
    </view>

    <view class="section">
      <text class="section-label">常用功能</text>

      <view
        class="tile tile--primary"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="goRepair"
      >
        <view class="tile-icon tile-icon--primary">
          <view class="icon-scan" />
        </view>
        <view class="tile-body">
          <text class="tile-title">扫码报修</text>
          <text class="tile-desc">扫标签二维码，或手输设备编码提交报修</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>

      <view
        class="tile"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="goAsset"
      >
        <view class="tile-icon">
          <view class="icon-search" />
        </view>
        <view class="tile-body">
          <text class="tile-title">资产查询</text>
          <text class="tile-desc">按编码、名称、序列号查找设备并查看详情</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>
    </view>

    <view class="tip">
      <text class="tip-title">使用提示</text>
      <text class="tip-text">设备标签二维码内容为设备编码；维修中、报废或已有进行中工单的设备不可再报修。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

function goAsset() {
  uni.navigateTo({ url: '/pages/asset/list' })
}

function goRepair() {
  uni.navigateTo({ url: '/pages/repair/scan' })
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
</script>

<style scoped>
.home {
  min-height: 100vh;
  padding: 0 0 64rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #dce9f8 0%, #f5f7fa 38%, #f5f7fa 100%);
}

.hero {
  padding: 36rpx 36rpx 48rpx;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 36rpx;
}

.brand {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #1a5fb4;
  letter-spacing: 3rpx;
  line-height: 1.1;
}

.brand-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #5b6b7c;
}

.logout {
  font-size: 26rpx;
  color: #1a5fb4;
  padding: 8rpx 4rpx 8rpx 20rpx;
}

.greet-hi {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: #1f2a37;
  line-height: 1.3;
}

.greet-meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #667085;
}

.section {
  margin: 0 28rpx;
}

.section-label {
  display: block;
  margin: 0 8rpx 20rpx;
  font-size: 24rpx;
  color: #98a2b3;
  letter-spacing: 1rpx;
}

.tile {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx 28rpx;
  margin-bottom: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  border: 1px solid rgba(26, 95, 180, 0.08);
  box-shadow: 0 8rpx 28rpx rgba(26, 95, 180, 0.06);
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.tile--primary {
  padding: 40rpx 28rpx;
  background: linear-gradient(135deg, #1a5fb4 0%, #2b7fd4 100%);
  border-color: transparent;
  box-shadow: 0 12rpx 36rpx rgba(26, 95, 180, 0.28);
}

.tile--pressed {
  transform: scale(0.985);
  opacity: 0.92;
}

.tile-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  background: #eef4fb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tile-icon--primary {
  background: rgba(255, 255, 255, 0.18);
}

.icon-scan {
  width: 36rpx;
  height: 36rpx;
  border: 4rpx solid #1a5fb4;
  border-radius: 6rpx;
  position: relative;
  box-sizing: border-box;
}

.icon-scan::after {
  content: '';
  position: absolute;
  left: -8rpx;
  right: -8rpx;
  top: 50%;
  height: 3rpx;
  margin-top: -1rpx;
  background: #1a5fb4;
}

.tile-icon--primary .icon-scan {
  border-color: #fff;
}

.tile-icon--primary .icon-scan::after {
  background: #fff;
}

.icon-search {
  width: 28rpx;
  height: 28rpx;
  border: 4rpx solid #1a5fb4;
  border-radius: 50%;
  position: relative;
  box-sizing: border-box;
}

.icon-search::after {
  content: '';
  position: absolute;
  width: 12rpx;
  height: 4rpx;
  background: #1a5fb4;
  right: -10rpx;
  bottom: -2rpx;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

.tile-body {
  flex: 1;
  min-width: 0;
}

.tile-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2a37;
  margin-bottom: 8rpx;
}

.tile--primary .tile-title {
  color: #fff;
  font-size: 36rpx;
}

.tile-desc {
  display: block;
  font-size: 24rpx;
  color: #667085;
  line-height: 1.45;
}

.tile--primary .tile-desc {
  color: rgba(255, 255, 255, 0.82);
}

.tile-arrow {
  font-size: 40rpx;
  color: #c0c4cc;
  line-height: 1;
  flex-shrink: 0;
}

.tile--primary .tile-arrow {
  color: rgba(255, 255, 255, 0.7);
}

.tip {
  margin: 36rpx 36rpx 0;
  padding: 0 8rpx;
}

.tip-title {
  display: block;
  font-size: 24rpx;
  color: #98a2b3;
  margin-bottom: 10rpx;
}

.tip-text {
  display: block;
  font-size: 22rpx;
  color: #98a2b3;
  line-height: 1.55;
}
</style>
