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
          {{ auth.user.tenantCode }} · 扫码查询 / 报修 / 运维执行
        </text>
      </view>
    </view>

    <view class="section">
      <text class="section-label">常用功能</text>

      <view
        class="tile tile--primary"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="go('/pages/asset/scan')"
      >
        <view class="tile-icon tile-icon--primary">
          <view class="icon-scan" />
        </view>
        <view class="tile-body">
          <text class="tile-title">扫码查询</text>
          <text class="tile-desc">扫标签查看台账详情</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>

      <view
        class="tile"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="go('/pages/repair/scan')"
      >
        <view class="tile-icon">
          <view class="icon-scan dark" />
        </view>
        <view class="tile-body">
          <text class="tile-title">扫码报修</text>
          <text class="tile-desc">扫码填报；可存草稿后提交</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>

      <view
        class="tile"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="go('/pages/repair/mine')"
      >
        <view class="tile-body">
          <text class="tile-title">我的报修 / 验收</text>
          <text class="tile-desc">申请单草稿·撤回；进度与验收</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>

      <view
        class="tile"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="go('/pages/asset/list')"
      >
        <view class="tile-icon">
          <view class="icon-search" />
        </view>
        <view class="tile-body">
          <text class="tile-title">资产查询</text>
          <text class="tile-desc">按编码、名称、序列号查找</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>

      <view
        class="tile"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="go('/pages/qc/adverse')"
      >
        <view class="tile-body">
          <text class="tile-title">不良事件上报</text>
          <text class="tile-desc">扫码带出设备，简易填报</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>
    </view>

    <view class="section">
      <text class="section-label">运维执行</text>
      <view
        v-for="item in opsTiles"
        :key="item.module"
        class="tile"
        hover-class="tile--pressed"
        :hover-stay-time="80"
        @click="goOps(item.module)"
      >
        <view class="tile-body">
          <text class="tile-title">{{ item.title }}</text>
          <text class="tile-desc">{{ item.desc }}</text>
        </view>
        <text class="tile-arrow">›</text>
      </view>
    </view>

    <view class="tip">
      <text class="tip-title">使用提示</text>
      <text class="tip-text">
        小程序在线使用，与 App/Web 同 API、同权限；不做离线盘点。标签二维码内容为设备编码。
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import type { OpsModule } from '@/config/ops'

const auth = useAuthStore()

const opsTiles: { module: OpsModule; title: string; desc: string }[] = [
  { module: 'maintain', title: '保养执行', desc: '扫码执行保养任务，可直开' },
  { module: 'inspect', title: '巡检执行', desc: '扫码执行巡检任务，可直开' },
  { module: 'pm', title: '预防性维护', desc: '扫码执行 PM 任务，可直开' }
]

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

function go(url: string) {
  uni.navigateTo({ url })
}

function goOps(module: OpsModule) {
  uni.navigateTo({ url: `/pages/ops/hub?module=${module}` })
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

<style lang="scss" scoped>
@import '../../uni.scss';

.home {
  min-height: 100vh;
  padding: 0 0 64rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, $meis-page-bg-top 0%, $meis-page-bg 36%, $meis-page-bg 100%);
}

.hero {
  padding: 32rpx 32rpx 40rpx;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32rpx;
}

.brand {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: $meis-primary;
  letter-spacing: 2rpx;
  line-height: 1.1;
}

.brand-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}

.logout {
  font-size: 26rpx;
  color: $meis-primary;
  padding: 8rpx 4rpx 8rpx 20rpx;
}

.greet-hi {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: $meis-text;
  line-height: 1.3;
}

.greet-meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}

.section {
  margin: 0 28rpx 24rpx;
}

.section-label {
  display: block;
  margin: 0 8rpx 16rpx;
  font-size: 24rpx;
  color: $meis-text-muted;
  letter-spacing: 1rpx;
}

.tile {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  background: #fff;
  border-radius: $meis-radius-lg;
  border: 1px solid $meis-border;
}

.tile--primary {
  padding: 36rpx 24rpx;
  background: $meis-primary;
  border-color: transparent;
}

.tile--pressed {
  opacity: 0.9;
}

.tile-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 16rpx;
  background: rgba(22, 119, 255, 0.08);
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
  border: 4rpx solid #fff;
  border-radius: 6rpx;
  position: relative;
  box-sizing: border-box;
}

.icon-scan.dark {
  border-color: $meis-primary;
}

.icon-scan::after {
  content: '';
  position: absolute;
  left: -8rpx;
  right: -8rpx;
  top: 50%;
  height: 3rpx;
  margin-top: -1rpx;
  background: #fff;
}

.icon-scan.dark::after {
  background: $meis-primary;
}

.icon-search {
  width: 28rpx;
  height: 28rpx;
  border: 4rpx solid $meis-primary;
  border-radius: 50%;
  position: relative;
  box-sizing: border-box;
}

.icon-search::after {
  content: '';
  position: absolute;
  width: 12rpx;
  height: 4rpx;
  background: $meis-primary;
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
  color: $meis-text;
  margin-bottom: 6rpx;
}

.tile--primary .tile-title {
  color: #fff;
  font-size: 34rpx;
}

.tile-desc {
  display: block;
  font-size: 24rpx;
  color: $meis-text-secondary;
  line-height: 1.4;
}

.tile--primary .tile-desc {
  color: rgba(255, 255, 255, 0.82);
}

.tile-arrow {
  font-size: 40rpx;
  color: $meis-text-muted;
  line-height: 1;
  flex-shrink: 0;
}

.tile--primary .tile-arrow {
  color: rgba(255, 255, 255, 0.7);
}

.tip {
  margin: 8rpx 36rpx 0;
  padding: 0 8rpx;
}

.tip-title {
  display: block;
  font-size: 24rpx;
  color: $meis-text-muted;
  margin-bottom: 8rpx;
}

.tip-text {
  display: block;
  font-size: 22rpx;
  color: $meis-text-muted;
  line-height: 1.55;
}
</style>
