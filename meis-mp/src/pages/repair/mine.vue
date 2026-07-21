<template>
  <view class="page">
    <view class="toolbar">
      <button size="mini" :type="pendingOnly ? 'primary' : 'default'" @click="togglePending">
        {{ pendingOnly ? '仅待验收' : '全部工单' }}
      </button>
      <button size="mini" @click="load">刷新</button>
    </view>
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!rows.length" class="empty">暂无工单</view>
    <view v-for="r in rows" :key="String(r.id)" class="card" @click="open(r)">
      <text class="no">{{ r.wo_no }}</text>
      <text class="meta">{{ r.device_name }} · {{ statusLabel(r.status) }}</text>
      <text class="desc">{{ r.fault_description }}</text>
      <text v-if="r.status === 'pending_verify'" class="badge">待验收</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const pendingOnly = ref(false)

const STATUS: Record<string, string> = {
  draft: '草稿',
  reported: '已报修',
  dispatching: '派工中',
  pending_accept: '待接单',
  accepted: '已接单',
  repairing: '维修中',
  suspended: '挂起',
  pending_verify: '待验收',
  verify_rejected: '拒绝验收',
  verified: '已验收',
  closed: '已关闭',
  cancelled: '已取消'
}

function statusLabel(s: unknown) {
  const k = String(s || '')
  return STATUS[k] || k || '—'
}

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  load()
})

function togglePending() {
  pendingOnly.value = !pendingOnly.value
  load()
}

async function load() {
  loading.value = true
  try {
    const data = await http.get<{ records?: Record<string, unknown>[] }>('/repair/workorder/mine', {
      page: 1,
      size: 50,
      ...(pendingOnly.value ? { pendingVerifyOnly: true } : {})
    })
    rows.value = Array.isArray(data?.records) ? data.records! : Array.isArray(data) ? (data as unknown as Record<string, unknown>[]) : []
  } catch (e: unknown) {
    rows.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function open(r: Record<string, unknown>) {
  uni.navigateTo({ url: `/pages/repair/detail?id=${r.id}` })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f5f7fa;
}
.toolbar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 20rpx;
}
.toolbar button {
  margin: 0;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: #98a2b3;
}
.card {
  position: relative;
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
}
.no {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.desc {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #1f2a37;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge {
  position: absolute;
  right: 24rpx;
  top: 28rpx;
  font-size: 22rpx;
  color: #1a5fb4;
  background: #e8f1fb;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
</style>
