<template>
  <view class="page">
    <view class="section-label">查询</view>
    <view class="row-actions">
      <button type="primary" size="mini" :disabled="looking" @click="onScan">扫码查询</button>
      <button size="mini" :disabled="looking" @click="goCreate()">直接新增</button>
    </view>
    <view class="search-row">
      <input
        class="input"
        v-model="keyword"
        placeholder="手工输入编码 / 名称"
        confirm-type="search"
        @confirm="onSearch"
      />
      <button size="mini" type="primary" :disabled="looking" @click="onSearch">搜索</button>
    </view>

    <view v-if="results.length" class="section-label">查询结果（点选操作）</view>
    <view
      v-for="t in results"
      :key="String(t.id)"
      class="card"
      hover-class="pressed"
      @click="openActions(t)"
    >
      <text class="title">{{ t.tag_code }} · {{ t.tag_name }}</text>
      <text class="meta">设备：{{ t.device_code || '—' }} {{ t.device_name || '' }}</text>
      <text class="meta muted">基站：{{ t.station_name || '—' }} · {{ t.is_active ? '启用' : '停用' }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

type TagRow = Record<string, unknown>

const auth = useAuthStore()
const keyword = ref('')
const results = ref<TagRow[]>([])
const looking = ref(false)

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

async function onScan() {
  try {
    const res = await uni.scanCode({ onlyFromCamera: false })
    const code = String(res.result || '').trim()
    if (!code) {
      uni.showToast({ title: '请扫描电流标签条码', icon: 'none' })
      return
    }
    await lookupExact(code)
  } catch {
    /* 用户取消 */
  }
}

async function lookupExact(code: string) {
  looking.value = true
  try {
    const data = await http.get<TagRow>(`/power/tag/by-code/${encodeURIComponent(code)}`)
    openActions(data)
  } catch (e: unknown) {
    const msg = (e as Error)?.message || ''
    if (msg.includes('不存在') || msg.includes('not found') || msg.includes('404')) {
      uni.showModal({
        title: '未找到标签',
        content: `编码「${code}」不存在，是否新增电流监测标签？`,
        success: (r) => {
          if (r.confirm) goCreate(code, true)
        }
      })
    } else {
      uni.showToast({ title: msg || '查询失败', icon: 'none' })
    }
  } finally {
    looking.value = false
  }
}

async function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    uni.showToast({ title: '请输入标签编码或名称', icon: 'none' })
    return
  }
  looking.value = true
  try {
    const page = await http.get<{ records?: TagRow[] }>('/power/tag/page', {
      page: 1,
      size: 50,
      keyword: kw
    })
    const list = page.records ?? []
    if (!list.length) {
      uni.showModal({
        title: '未找到标签',
        content: `未查询到「${kw}」，是否新增？`,
        success: (r) => {
          if (r.confirm) goCreate(kw, false)
        }
      })
      results.value = []
      return
    }
    results.value = list
    if (list.length === 1) openActions(list[0])
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '查询失败', icon: 'none' })
  } finally {
    looking.value = false
  }
}

function goCreate(code?: string, lock?: boolean) {
  const q: string[] = []
  if (code) q.push(`tagCode=${encodeURIComponent(code)}`)
  if (lock) q.push('lockCode=1')
  uni.navigateTo({ url: `/pages/power/form${q.length ? `?${q.join('&')}` : ''}` })
}

function openActions(tag: TagRow) {
  const id = String(tag.id ?? '')
  uni.showActionSheet({
    itemList: ['维护标签信息', '查看绑定设备', '修改绑定设备', '监测记录'],
    success: (res) => {
      if (res.tapIndex === 0) {
        uni.navigateTo({ url: `/pages/power/form?id=${encodeURIComponent(id)}` })
      } else if (res.tapIndex === 1) {
        uni.navigateTo({ url: `/pages/power/bind?id=${encodeURIComponent(id)}&viewOnly=1` })
      } else if (res.tapIndex === 2) {
        uni.navigateTo({ url: `/pages/power/bind?id=${encodeURIComponent(id)}` })
      } else if (res.tapIndex === 3) {
        const path = encodeURIComponent(`/power/tag/${id}/readings/page`)
        const title = encodeURIComponent(`监测记录 · ${tag.tag_code || ''}`)
        uni.navigateTo({ url: `/pages/power/readings?path=${path}&title=${title}` })
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
  margin: 8rpx 8rpx 16rpx;
  font-size: 24rpx;
  color: $meis-text-muted;
}
.row-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.search-row {
  display: flex;
  gap: 12rpx;
  margin-bottom: 24rpx;
  align-items: center;
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
.pressed {
  opacity: 0.9;
}
.title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $meis-text-primary;
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
</style>
