<template>
  <view class="page">
    <view class="section-label">设备调配</view>
    <view
      v-for="item in entries"
      :key="item.path"
      class="card tile"
      hover-class="tile--pressed"
      :hover-stay-time="80"
      @click="go(item.path)"
    >
      <view class="tile-body">
        <text class="tile-title">{{ item.title }}</text>
        <text class="tile-desc">{{ item.desc }}</text>
      </view>
      <text class="tile-arrow">›</text>
    </view>
    <view v-if="!entries.length" class="empty">暂无可用功能权限</view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { hasMenu } from '@/utils/permission'

const auth = useAuthStore()

const ALL = [
  {
    code: 'shared_loan',
    title: '借调申请',
    desc: '新建、提交借调；审批通过后确认借出',
    path: '/pages/shared/loan-list'
  },
  {
    code: 'shared_loan_approve',
    title: '借调审核',
    desc: '审核待审批的借调申请',
    path: '/pages/shared/loan-approve'
  },
  {
    code: 'shared_return',
    title: '归还申请',
    desc: '对借出中设备发起归还',
    path: '/pages/shared/return-list'
  },
  {
    code: 'shared_return_approve',
    title: '归还审核',
    desc: '审核待审批的归还申请',
    path: '/pages/shared/return-approve'
  }
] as const

const entries = computed(() => ALL.filter((e) => hasMenu(auth.user, e.code)))

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

function go(path: string) {
  uni.navigateTo({ url: path })
}
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.page {
  min-height: 100vh;
  padding: 24rpx;
  background: $meis-page-bg;
  box-sizing: border-box;
}
.section-label {
  margin: 0 8rpx 16rpx;
  font-size: 24rpx;
  color: $meis-text-muted;
}
.card {
  background: #fff;
  border-radius: $meis-radius-lg;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  border: 1px solid $meis-border;
}
.tile {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.tile--pressed {
  opacity: 0.9;
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
}
.tile-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}
.tile-arrow {
  font-size: 40rpx;
  color: $meis-text-muted;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: $meis-text-muted;
}
</style>
