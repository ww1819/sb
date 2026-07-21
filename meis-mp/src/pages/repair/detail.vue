<template>
  <view class="page">
    <view v-if="loading" class="empty">加载中…</view>
    <template v-else-if="wo">
      <view class="card">
        <text class="no">{{ wo.wo_no }}</text>
        <text class="meta">{{ wo.device_name }}（{{ wo.device_code }}）</text>
        <text class="meta">状态：{{ statusLabel(wo.status) }}</text>
        <text class="desc">{{ wo.fault_description }}</text>
      </view>

      <view class="card">
        <text class="section">进度</text>
        <view v-for="(m, i) in milestones" :key="i" class="line">
          <text class="line-title">{{ m.title || m.name || m.event_type }}</text>
          <text class="line-meta">{{ m.at || m.time || m.created_at || '' }}</text>
        </view>
        <view v-if="!milestones.length" class="empty-sm">暂无时间线</view>
      </view>

      <view v-if="canVerify" class="actions">
        <button type="primary" :loading="submitting" @click="verifyPass">验收通过</button>
        <button :loading="submitting" @click="verifyFail">拒绝验收</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const id = ref('')
const wo = ref<Record<string, unknown> | null>(null)
const milestones = ref<Record<string, unknown>[]>([])
const loading = ref(true)
const submitting = ref(false)

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

const canVerify = computed(() => wo.value?.status === 'pending_verify')

onLoad((q) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  id.value = String(q?.id || '')
  load()
})

async function load() {
  loading.value = true
  try {
    wo.value = await http.get(`/repair/workorder/${id.value}`)
    const tl = await http.get<Record<string, unknown>>(`/repair/workorder/${id.value}/timeline`)
    const ms = Array.isArray(tl?.milestones) ? (tl.milestones as Record<string, unknown>[]) : []
    const ev = Array.isArray(tl?.events) ? (tl.events as Record<string, unknown>[]) : []
    milestones.value = ms.length ? ms : ev
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function verifyPass() {
  const rating = await new Promise<number | null>((resolve) => {
    uni.showActionSheet({
      itemList: ['5 星', '4 星', '3 星', '2 星', '1 星'],
      success: (r) => resolve(5 - r.tapIndex),
      fail: () => resolve(null)
    })
  })
  if (rating == null) return
  submitting.value = true
  try {
    await http.post(`/repair/workorder/${id.value}/verify`, {
      client: 'mp',
      verify_result: 'pass',
      satisfaction_rating: rating
    })
    uni.showToast({ title: '验收通过', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

async function verifyFail() {
  uni.showModal({
    title: '拒绝验收',
    editable: true,
    placeholderText: '请填写拒绝原因',
    success: async (res) => {
      if (!res.confirm) return
      const comment = (res.content || '').trim()
      if (!comment) {
        uni.showToast({ title: '请填写拒绝原因', icon: 'none' })
        return
      }
      submitting.value = true
      try {
        await http.post(`/repair/workorder/${id.value}/verify`, {
          client: 'mp',
          verify_result: 'fail',
          verify_comment: comment
        })
        uni.showToast({ title: '已拒绝', icon: 'success' })
        await load()
      } catch (e: unknown) {
        uni.showToast({ title: e instanceof Error ? e.message : '失败', icon: 'none' })
      } finally {
        submitting.value = false
      }
    }
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 24rpx 80rpx;
  background: #f5f7fa;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: #98a2b3;
}
.empty-sm {
  font-size: 24rpx;
  color: #98a2b3;
  padding: 12rpx 0;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}
.no {
  display: block;
  font-size: 32rpx;
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
  margin-top: 16rpx;
  font-size: 28rpx;
  line-height: 1.5;
}
.section {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}
.line {
  padding: 12rpx 0;
  border-bottom: 1px solid #f0f2f5;
}
.line-title {
  display: block;
  font-size: 26rpx;
}
.line-meta {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #98a2b3;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.actions button {
  margin: 0;
}
</style>
