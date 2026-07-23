<template>
  <view class="page">
    <view class="tabs">
      <button size="mini" :type="tab === 'apply' ? 'primary' : 'default'" @click="switchTab('apply')">
        申请单
      </button>
      <button size="mini" :type="tab === 'progress' ? 'primary' : 'default'" @click="switchTab('progress')">
        进度验收
      </button>
      <button size="mini" @click="load">刷新</button>
      <button v-if="tab === 'apply'" size="mini" type="primary" @click="goNew">新建报修</button>
    </view>
    <view v-if="tab === 'progress'" class="toolbar">
      <button size="mini" :type="pendingOnly ? 'primary' : 'default'" @click="togglePending">
        {{ pendingOnly ? '仅待验收' : '全部工单' }}
      </button>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!rows.length" class="empty">
      {{ tab === 'apply' ? '暂无申请单/草稿' : '暂无工单' }}
    </view>

    <view v-for="r in rows" :key="String(r.id)" class="card">
      <view @click="open(r)">
        <text class="no">{{ r.wo_no || '草稿' }}</text>
        <text class="meta">{{ r.device_name }} · {{ statusLabel(r.status) }}</text>
        <text class="desc">{{ r.fault_description }}</text>
        <text v-if="r.status === 'pending_verify'" class="badge">待验收</text>
      </view>
      <view v-if="tab === 'apply'" class="row-ops" @click.stop>
        <button v-if="r.status === 'draft'" size="mini" type="primary" @click="submitRow(r)">提交</button>
        <button v-if="r.status === 'draft'" size="mini" @click="editRow(r)">编辑</button>
        <button v-if="canWithdraw(r)" size="mini" @click="withdrawRow(r)">撤回</button>
        <button v-if="r.status === 'draft'" size="mini" @click="deleteRow(r)">删除</button>
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
const pendingOnly = ref(false)
const tab = ref<'apply' | 'progress'>('apply')

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

function canWithdraw(r: Record<string, unknown>) {
  return String(r.status || '') === 'reported'
}

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  load()
})

function switchTab(t: 'apply' | 'progress') {
  tab.value = t
  load()
}

function togglePending() {
  pendingOnly.value = !pendingOnly.value
  load()
}

function goNew() {
  uni.navigateTo({ url: '/pages/repair/scan' })
}

function editRow(r: Record<string, unknown>) {
  uni.navigateTo({ url: `/pages/repair/scan?id=${r.id}` })
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'apply') {
      const data = await http.get<{ records?: Record<string, unknown>[] }>('/repair/workorder/page', {
        page: 1,
        size: 50,
        mode: 'apply'
      })
      rows.value = Array.isArray(data?.records)
        ? data.records!
        : Array.isArray(data)
          ? (data as unknown as Record<string, unknown>[])
          : []
    } else {
      const data = await http.get<{ records?: Record<string, unknown>[] }>('/repair/workorder/mine', {
        page: 1,
        size: 50,
        ...(pendingOnly.value ? { pendingVerifyOnly: true } : {})
      })
      rows.value = Array.isArray(data?.records)
        ? data.records!
        : Array.isArray(data)
          ? (data as unknown as Record<string, unknown>[])
          : []
    }
  } catch (e: unknown) {
    rows.value = []
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function open(r: Record<string, unknown>) {
  if (tab.value === 'apply' && String(r.status) === 'draft') {
    editRow(r)
    return
  }
  uni.navigateTo({ url: `/pages/repair/detail?id=${r.id}` })
}

async function submitRow(r: Record<string, unknown>) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '提交报修',
      content: '提交后将进入维修流程，是否继续？',
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  try {
    await http.post(`/repair/workorder/${r.id}/submit`)
    uni.showToast({ title: '已提交', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '提交失败', icon: 'none' })
  }
}

async function withdrawRow(r: Record<string, unknown>) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '撤回报修',
      content: '撤回后将回到草稿，可再次修改并提交。',
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  try {
    await http.post(`/repair/workorder/${r.id}/withdraw`, { remark: '用户撤回', client: 'mp' })
    uni.showToast({ title: '已撤回', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '撤回失败', icon: 'none' })
  }
}

async function deleteRow(r: Record<string, unknown>) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '删除草稿',
      content: '确认删除该草稿？',
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  try {
    await http.delete(`/repair/workorder/${r.id}`)
    uni.showToast({ title: '已删除', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '删除失败', icon: 'none' })
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f5f7fa;
}
.tabs,
.toolbar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 16rpx;
  flex-wrap: wrap;
}
.tabs button,
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
.row-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
  padding-top: 12rpx;
  border-top: 1px solid #f0f2f5;
}
.row-ops button {
  margin: 0;
}
</style>
