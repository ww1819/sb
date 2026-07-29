<template>
  <view class="page">
    <view class="section-label">查询（无扫码）</view>
    <view class="search-row">
      <input class="input" v-model="keyword" placeholder="编码 / 名称 / 位置" confirm-type="search" @confirm="search" />
      <button size="mini" type="primary" :loading="looking" @click="search">搜索</button>
      <button size="mini" @click="goForm()">新增</button>
    </view>

    <view v-for="s in rows" :key="String(s.id)" class="card" @click="openActions(s)">
      <text class="title">{{ s.station_code }} · {{ s.station_name }}</text>
      <text class="meta">位置：{{ s.location || '—' }} · {{ s.campus_name || '' }}</text>
      <text class="meta muted">{{ s.status || '' }} · {{ s.is_active ? '启用' : '停用' }}</text>
    </view>
    <view v-if="!rows.length && !looking" class="empty">暂无基站</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

type Row = Record<string, unknown>

const auth = useAuthStore()
const keyword = ref('')
const rows = ref<Row[]>([])
const looking = ref(false)

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  search()
})

async function search() {
  looking.value = true
  try {
    const data = await http.get<{ records?: Row[] }>('/power/station/page', {
      page: 1,
      size: 50,
      keyword: keyword.value.trim() || undefined
    })
    rows.value = data.records ?? []
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '查询失败', icon: 'none' })
  } finally {
    looking.value = false
  }
}

function goForm(id?: string) {
  const q = id ? `?id=${encodeURIComponent(id)}` : ''
  uni.navigateTo({ url: `/pages/power/station-form${q}` })
}

function openActions(s: Row) {
  const id = String(s.id ?? '')
  uni.showActionSheet({
    itemList: ['修改基站', '监测记录'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goForm(id)
      } else if (res.tapIndex === 1) {
        const path = encodeURIComponent(`/power/station/${id}/readings/page`)
        const title = encodeURIComponent(`监测记录 · ${s.station_code || ''}`)
        uni.navigateTo({
          url: `/pages/power/readings?path=${path}&title=${title}&showStation=1`
        })
      }
    }
  })
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
.search-row {
  display: flex;
  gap: 12rpx;
  align-items: center;
  margin-bottom: 20rpx;
}
.input {
  flex: 1;
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-md;
  padding: 16rpx 20rpx;
  font-size: 28rpx;
}
.card {
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-lg;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.title {
  display: block;
  font-weight: 600;
  font-size: 30rpx;
}
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $meis-text-secondary;
}
.muted {
  color: $meis-text-muted;
}
.empty {
  text-align: center;
  color: $meis-text-muted;
  margin-top: 48rpx;
}
</style>
