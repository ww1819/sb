<template>
  <view class="page">
    <view class="section-label">筛选（默认近 1 小时，人工确认采集）</view>
    <view class="field" @click="pickFrom">
      <text class="label">开始</text>
      <text class="value">{{ from || '请选择' }}</text>
    </view>
    <view class="field" @click="pickTo">
      <text class="label">结束</text>
      <text class="value">{{ to || '请选择' }}</text>
    </view>
    <view class="field">
      <text class="label">排序</text>
      <picker :range="sortLabels" :value="sortIndex" @change="onSort">
        <text class="value">{{ sortLabels[sortIndex] }}</text>
      </picker>
    </view>
    <button type="primary" size="mini" :loading="loading" @click="reload">查询</button>

    <view v-if="!rows.length && !loading" class="empty">暂无读数</view>
    <view v-for="r in rows" :key="String(r.id)" class="card">
      <text class="title">{{ r.tag_code || '—' }} · {{ r.current_ma ?? '—' }} mA</text>
      <text class="meta">设备：{{ r.device_code || '—' }}{{ showStation ? ` · 基站：${r.station_code || '—'}` : '' }}</text>
      <text class="meta muted">读取 {{ fmt(r.read_at) }} · 入库 {{ fmt(r.created_at) }}</text>
    </view>

    <view v-if="total > 0" class="pager">
      <text>共 {{ total }} · {{ page }}/{{ maxPage }}</text>
      <view class="pager-btns">
        <button size="mini" :disabled="page <= 1 || loading" @click="prev">上一页</button>
        <button size="mini" :disabled="page >= maxPage || loading" @click="next">下一页</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatDisplayDateTime, toDateTimeParam, DATETIME_FMT } from '@/utils/datetime'

type Row = Record<string, unknown>

const auth = useAuthStore()
const listPath = ref('')
const showStation = ref(false)
const loading = ref(false)
const rows = ref<Row[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const sortOrder = ref('desc')
const sortLabels = ['读取时间↓', '读取时间↑']
const sortIndex = computed(() => (sortOrder.value === 'asc' ? 1 : 0))
const from = ref('')
const to = ref('')

const maxPage = computed(() => (total.value === 0 ? 1 : Math.ceil(total.value / size)))

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) uni.reLaunch({ url: '/pages/login/index' })
})

onLoad((q) => {
  listPath.value = decodeURIComponent(String(q?.path || ''))
  showStation.value = q?.showStation === '1'
  const title = q?.title ? decodeURIComponent(String(q.title)) : '监测记录'
  uni.setNavigationBarTitle({ title })
  const now = new Date()
  const hourAgo = new Date(now.getTime() - 60 * 60 * 1000)
  to.value = formatDateTime(now)
  from.value = formatDateTime(hourAgo)
  load()
})

function fmt(v: unknown) {
  return formatDisplayDateTime(v)
}

function pickFrom() {
  // 简化：用日期+默认时间；微信小程序可用 picker mode=date 再手改
  uni.showModal({
    title: '开始时间',
    editable: true,
    placeholderText: DATETIME_FMT,
    content: from.value,
    success: (r) => {
      if (r.confirm && r.content) {
        from.value = toDateTimeParam(r.content.trim()) || r.content.trim()
      }
    }
  })
}

function pickTo() {
  uni.showModal({
    title: '结束时间',
    editable: true,
    placeholderText: DATETIME_FMT,
    content: to.value,
    success: (r) => {
      if (r.confirm && r.content) {
        to.value = toDateTimeParam(r.content.trim()) || r.content.trim()
      }
    }
  })
}

function onSort(e: { detail: { value: string } }) {
  sortOrder.value = Number(e.detail.value) === 1 ? 'asc' : 'desc'
}

function reload() {
  page.value = 1
  load()
}

function prev() {
  if (page.value <= 1) return
  page.value--
  load()
}

function next() {
  if (page.value >= maxPage.value) return
  page.value++
  load()
}

async function load() {
  if (!listPath.value) return
  loading.value = true
  try {
    const data = await http.get<{ records?: Row[]; total?: number }>(listPath.value, {
      page: page.value,
      size,
      readAtFrom: from.value,
      readAtTo: to.value,
      sortOrder: sortOrder.value
    })
    rows.value = data.records ?? []
    total.value = Number(data.total ?? rows.value.length)
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '加载失败', icon: 'none' })
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
.section-label {
  margin: 0 8rpx 16rpx;
  font-size: 24rpx;
  color: $meis-text-muted;
}
.field {
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-md;
  padding: 20rpx 24rpx;
  margin-bottom: 12rpx;
}
.label {
  display: block;
  font-size: 24rpx;
  color: $meis-text-muted;
  margin-bottom: 6rpx;
}
.value {
  font-size: 28rpx;
}
.card {
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-lg;
  padding: 24rpx;
  margin-top: 16rpx;
}
.title {
  display: block;
  font-weight: 600;
  font-size: 28rpx;
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
.pager {
  margin-top: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 24rpx;
}
.pager-btns {
  display: flex;
  gap: 12rpx;
}
</style>
