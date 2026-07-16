<template>
  <view class="asset-list">
    <view class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="设备编码 / 名称 / 序列号"
        confirm-type="search"
        @confirm="onSearch"
      />
      <button class="search-btn" size="mini" @click="onSearch">搜索</button>
    </view>

    <view v-if="loading && !rows.length" class="empty">加载中…</view>
    <view v-else-if="!rows.length" class="empty">暂无数据，请输入关键字搜索</view>

    <view
      v-for="item in rows"
      :key="String(item.id)"
      class="item"
      @click="goDetail(item)"
    >
      <view class="item-main">
        <text class="code">{{ item.device_code }}</text>
        <text class="name">{{ item.device_name }}</text>
      </view>
      <view class="item-meta">
        <text>{{ item.dept_name || item.model || '—' }}</text>
        <text class="status">{{ statusLabel(item.device_status) }}</text>
      </view>
    </view>

    <view v-if="rows.length && hasMore" class="more" @click="loadMore">加载更多</view>
    <view v-else-if="rows.length" class="more muted">没有更多了</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

interface DeviceRow {
  id: string
  device_code?: string
  device_name?: string
  dept_name?: string
  model?: string
  device_status?: string
}

const auth = useAuthStore()
const keyword = ref('')
const rows = ref<DeviceRow[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)

const hasMore = computed(() => rows.value.length < total.value)

const STATUS_MAP: Record<string, string> = {
  normal: '正常',
  maintenance: '维修中',
  pending_verify: '待验收',
  scrap: '报废',
  idle: '闲置'
}

function statusLabel(s?: string) {
  if (!s) return '—'
  return STATUS_MAP[s] || s
}

async function load(reset = false) {
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  if (reset) {
    page.value = 1
    rows.value = []
  }
  loading.value = true
  try {
    const data = await http.get<{ records: DeviceRow[]; total: number }>('/asset/device/page', {
      page: page.value,
      size,
      keyword: keyword.value.trim() || undefined
    })
    const list = data?.records ?? []
    total.value = data?.total ?? 0
    rows.value = reset ? list : rows.value.concat(list)
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load(true)
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value += 1
  load(false)
}

function goDetail(item: DeviceRow) {
  uni.navigateTo({ url: `/pages/asset/detail?id=${item.id}` })
}

onShow(() => {
  auth.restore()
  if (!rows.value.length) load(true)
})
</script>

<style scoped>
.asset-list {
  padding: 24rpx 24rpx 48rpx;
}
.search-bar {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
  align-items: center;
}
.search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  background: #fff;
  border-radius: 12rpx;
  border: 1px solid #e4e7ec;
}
.search-btn {
  background: #1a5fb4;
  color: #fff;
  margin: 0;
}
.item {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
}
.item-main {
  margin-bottom: 12rpx;
}
.code {
  display: block;
  font-size: 24rpx;
  color: #1a5fb4;
  margin-bottom: 6rpx;
}
.name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}
.item-meta {
  display: flex;
  justify-content: space-between;
  font-size: 24rpx;
  color: #667085;
}
.status {
  color: #344054;
}
.empty,
.more {
  text-align: center;
  padding: 40rpx;
  color: #98a2b3;
  font-size: 26rpx;
}
.muted {
  color: #c0c4cc;
}
</style>
