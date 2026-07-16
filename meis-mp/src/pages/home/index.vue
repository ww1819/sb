<template>
  <view class="home-page">
    <view class="header">
      <view>
        <text class="hello">你好，{{ auth.displayName || '用户' }}</text>
        <text class="tenant" v-if="auth.user?.tenantCode">医院：{{ auth.user.tenantCode }}</text>
      </view>
      <text class="logout" @click="onLogout">退出</text>
    </view>

    <view class="card" @click="goAsset">
      <text class="card-title">资产查询</text>
      <text class="card-desc">按编码、名称搜索设备台账并查看详情</text>
    </view>

    <view class="card card--accent" @click="goRepair">
      <text class="card-title">扫码报修</text>
      <text class="card-desc">扫描设备标签二维码，或手动输入设备编码报修</text>
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
.home-page {
  padding: 40rpx 32rpx;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 40rpx;
}
.hello {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}
.tenant {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.logout {
  color: #1a5fb4;
  font-size: 26rpx;
  padding: 8rpx;
}
.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 40rpx 32rpx;
  margin-bottom: 24rpx;
  border: 1px solid #e8eef5;
  box-shadow: 0 4rpx 16rpx rgba(16, 24, 40, 0.04);
}
.card--accent {
  border-color: #cfe0f5;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 60%);
}
.card-title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: #1a5fb4;
  margin-bottom: 12rpx;
}
.card-desc {
  display: block;
  font-size: 26rpx;
  color: #667085;
  line-height: 1.5;
}
</style>
