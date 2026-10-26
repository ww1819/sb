<template>
  <view class="msg-page">
    <view class="msg-head">
      <text class="msg-title">消息</text>
      <text class="msg-refresh" @click="load">刷新</text>
    </view>

    <view v-if="loading" class="msg-empty">加载中…</view>
    <view v-else-if="!items.length" class="msg-empty">暂无消息</view>
    <scroll-view v-else scroll-y class="msg-list" @scrolltolower="noop">
      <view
        v-for="m in items"
        :key="String(m.id)"
        class="msg-card"
        hover-class="msg-card--pressed"
        :hover-stay-time="80"
        @click="onTap(m)"
      >
        <view class="msg-card__row">
          <view v-if="!m.is_read" class="msg-dot" />
          <text class="msg-card__title" :class="{ unread: !m.is_read }">
            {{ m.title || m.message_type || '通知' }}
          </text>
          <text class="msg-card__time">{{ formatTime(m.created_at || m.send_time) }}</text>
        </view>
        <text class="msg-card__body">{{ m.content || m.body || '—' }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

type Msg = Record<string, unknown>

const auth = useAuthStore()
const loading = ref(false)
const items = ref<Msg[]>([])

function noop() {}

function formatTime(v: unknown) {
  if (!v) return ''
  const s = String(v)
  if (s.length >= 16) return s.slice(0, 16).replace('T', ' ')
  return s
}

async function load() {
  if (!auth.isLoggedIn) return
  loading.value = true
  try {
    const data = await http.get<Msg[] | { records?: Msg[]; list?: Msg[] }>('/notification/messages')
    if (Array.isArray(data)) {
      items.value = data
    } else if (data && typeof data === 'object') {
      items.value = data.records ?? data.list ?? []
    } else {
      items.value = []
    }
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function onTap(m: Msg) {
  const id = m.id != null ? String(m.id) : ''
  const type = String(m.message_type ?? m.notification_type ?? '')
  try {
    if (id && m.is_read !== true) {
      await http.post(`/notification/messages/${id}/read`, {})
      m.is_read = true
    }
  } catch {
    /* ignore */
  }
  if (/ops|maintain|inspect|pm/i.test(type)) {
    let module = 'maintain'
    if (/inspect/i.test(type)) module = 'inspect'
    else if (/pm/i.test(type)) module = 'pm'
    uni.navigateTo({ url: `/pages/ops/hub?module=${module}` })
    return
  }
  if (/repair|workorder/i.test(type)) {
    uni.navigateTo({ url: '/pages/repair/mine' })
  }
}

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  load()
})
</script>

<style lang="scss" scoped>
@import '../../uni.scss';

.msg-page {
  min-height: 100vh;
  background: $meis-page-bg;
  box-sizing: border-box;
  padding-bottom: 24rpx;
}
.msg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
  background: linear-gradient(180deg, $meis-page-bg-top 0%, $meis-page-bg 100%);
}
.msg-title {
  font-size: 40rpx;
  font-weight: 600;
  color: $meis-text;
}
.msg-refresh {
  font-size: 26rpx;
  color: $meis-primary;
}
.msg-empty {
  padding: 160rpx 32rpx;
  text-align: center;
  color: $meis-text-muted;
  font-size: 28rpx;
}
.msg-list {
  max-height: calc(100vh - 120rpx);
  padding: 8rpx 24rpx 32rpx;
  box-sizing: border-box;
}
.msg-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 16rpx;
}
.msg-card--pressed {
  opacity: 0.85;
}
.msg-card__row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}
.msg-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: $meis-primary;
  flex-shrink: 0;
}
.msg-card__title {
  flex: 1;
  font-size: 30rpx;
  color: $meis-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-card__title.unread {
  font-weight: 600;
}
.msg-card__time {
  font-size: 22rpx;
  color: $meis-text-muted;
  flex-shrink: 0;
}
.msg-card__body {
  display: block;
  font-size: 26rpx;
  color: $meis-text-secondary;
  line-height: 1.5;
}
</style>
