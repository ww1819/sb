<template>
  <view class="page">
    <view class="tabs">
      <button size="mini" @click="load">刷新</button>
      <button size="mini" type="primary" @click="goNew">申请归还</button>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!rows.length" class="empty">暂无归还单</view>

    <view v-for="r in rows" :key="String(r.id)" class="card">
      <text class="no">{{ r.return_no || '—' }}</text>
      <text class="meta">{{ r.device_name || '—' }} · {{ statusLabel(r.status) }}</text>
      <text class="desc">
        借调 {{ r.loan_no || '—' }}
        <text v-if="r.return_date"> · {{ formatDisplayDate(r.return_date) }}</text>
      </text>
      <text v-if="r.condition_desc" class="desc">状况：{{ r.condition_desc }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatDisplayDate } from '@/utils/datetime'

const auth = useAuthStore()
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)

const RETURN_STATUS: Record<string, string> = {
  pending: '待审核',
  approved: '已批准',
  rejected: '已驳回'
}

function statusLabel(s: unknown) {
  const k = String(s || '')
  return RETURN_STATUS[k] || k || '—'
}

function asRecords(data: unknown): Record<string, unknown>[] {
  if (Array.isArray(data)) return data as Record<string, unknown>[]
  if (data && typeof data === 'object' && Array.isArray((data as { records?: unknown[] }).records)) {
    return (data as { records: Record<string, unknown>[] }).records
  }
  return []
}

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  load()
})

function goNew() {
  uni.navigateTo({ url: '/pages/shared/return-form' })
}

async function load() {
  loading.value = true
  try {
    const data = await http.get<unknown>('/shared/return/page', { page: 1, size: 50 })
    rows.value = asRecords(data)
  } catch (e: unknown) {
    rows.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
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
.tabs {
  display: flex;
  gap: 12rpx;
  margin-bottom: 16rpx;
  flex-wrap: wrap;
}
.tabs button {
  margin: 0;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: $meis-text-muted;
}
.card {
  background: #fff;
  border-radius: $meis-radius-lg;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  border: 1px solid $meis-border;
}
.no {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $meis-text;
}
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}
.desc {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: $meis-text;
}
</style>
