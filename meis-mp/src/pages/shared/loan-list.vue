<template>
  <view class="page">
    <view class="tabs">
      <button size="mini" @click="load">刷新</button>
      <button size="mini" type="primary" @click="goNew">新建借调</button>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!rows.length" class="empty">暂无借调单</view>

    <view v-for="r in rows" :key="String(r.id)" class="card">
      <view @click="open(r)">
        <text class="no">{{ r.loan_no || '草稿' }}</text>
        <text class="meta">{{ r.device_name || '—' }} · {{ statusLabel(r.status) }}</text>
        <text class="desc">
          {{ r.device_code || '' }}
          <text v-if="r.to_dept_name"> · 借入 {{ r.to_dept_name }}</text>
        </text>
      </view>
      <view class="row-ops" @click.stop>
        <button v-if="r.status === 'draft'" size="mini" type="primary" @click="submitRow(r)">
          提交
        </button>
        <button v-if="r.status === 'draft' || r.status === 'pending'" size="mini" @click="editRow(r)">
          编辑
        </button>
        <button v-if="r.status === 'approved'" size="mini" type="primary" @click="lendRow(r)">
          确认借出
        </button>
      </view>
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

const LOAN_STATUS: Record<string, string> = {
  draft: '草稿',
  pending: '待审核',
  approved: '已批准',
  on_loan: '借出中',
  returned: '已归还',
  rejected: '已驳回'
}

function statusLabel(s: unknown) {
  const k = String(s || '')
  return LOAN_STATUS[k] || k || '—'
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
  uni.navigateTo({ url: '/pages/shared/loan-form' })
}

function editRow(r: Record<string, unknown>) {
  uni.navigateTo({ url: `/pages/shared/loan-form?id=${r.id}` })
}

function open(r: Record<string, unknown>) {
  if (r.status === 'draft' || r.status === 'pending') {
    editRow(r)
    return
  }
  editRow(r)
}

async function load() {
  loading.value = true
  try {
    const data = await http.get<unknown>('/shared/loan/page', { page: 1, size: 50 })
    rows.value = asRecords(data)
  } catch (e: unknown) {
    rows.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function submitRow(r: Record<string, unknown>) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '提交借调',
      content: '提交后进入审核流程，是否继续？',
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  try {
    await http.post(`/shared/loan/${r.id}/submit`, { applicantId: auth.user?.userId, client: 'mp' })
    uni.showToast({ title: '已提交', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '提交失败', icon: 'none' })
  }
}

async function lendRow(r: Record<string, unknown>) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '确认借出',
      content: `确认将「${r.device_name || '设备'}」借出？`,
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  try {
    await http.post(`/shared/loan/${r.id}/lend`, { client: 'mp' })
    uni.showToast({ title: '已借出', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '借出失败', icon: 'none' })
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
.row-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
  padding-top: 12rpx;
  border-top: 1px solid $meis-border;
}
.row-ops button {
  margin: 0;
}
</style>
