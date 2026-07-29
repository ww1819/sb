<template>
  <view class="page">
    <view v-if="tag" class="head">
      <text class="title">{{ tag.tag_code }} · {{ tag.tag_name }}</text>
    </view>

    <view class="section-label">当前绑定</view>
    <view class="card">
      <text v-if="!device">未绑定设备</text>
      <view v-else>
        <text class="title">{{ device.device_code }} · {{ device.device_name }}</text>
      </view>
    </view>

    <template v-if="!viewOnly">
      <view class="section-label">选择设备</view>
      <view class="row-actions">
        <button size="mini" type="primary" @click="scanDevice">扫设备码</button>
        <button size="mini" @click="searchDevice">搜索</button>
      </view>
      <input class="input" v-model="keyword" placeholder="设备编码 / 名称" confirm-type="search" @confirm="searchDevice" />

      <view
        v-for="d in candidates"
        :key="String(d.id || d.device_id)"
        class="card"
        :class="{ selected: isSelected(d) }"
        @click="device = d"
      >
        <text>{{ d.device_code }} · {{ d.device_name }}</text>
      </view>

      <button type="primary" :loading="saving" @click="save(false)">保存绑定</button>
      <button class="mt" :disabled="!device || saving" @click="save(true)">解绑设备</button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

type Row = Record<string, unknown>

const auth = useAuthStore()
const tagId = ref('')
const viewOnly = ref(false)
const tag = ref<Row | null>(null)
const device = ref<Row | null>(null)
const candidates = ref<Row[]>([])
const keyword = ref('')
const saving = ref(false)

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) uni.reLaunch({ url: '/pages/login/index' })
})

onLoad(async (q) => {
  tagId.value = String(q?.id || '')
  viewOnly.value = q?.viewOnly === '1'
  if (tagId.value) await loadTag()
})

async function loadTag() {
  try {
    const m = await http.get<Row>(`/power/tag/${tagId.value}`)
    tag.value = m
    if (m.device_id) {
      device.value = {
        id: m.device_id,
        device_code: m.device_code,
        device_name: m.device_name
      }
    } else {
      device.value = null
    }
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '加载失败', icon: 'none' })
  }
}

function isSelected(d: Row) {
  const a = String(device.value?.id ?? device.value?.device_id ?? '')
  const b = String(d.id ?? d.device_id ?? '')
  return a && a === b
}

async function scanDevice() {
  try {
    const res = await uni.scanCode({ onlyFromCamera: false })
    const code = String(res.result || '').trim()
    if (!code) return
    keyword.value = code
    try {
      const data = await http.get<Row>(`/asset/device/by-code/${encodeURIComponent(code)}`)
      device.value = data
      candidates.value = [data]
    } catch {
      await searchDevice()
    }
  } catch {
    /* cancel */
  }
}

async function searchDevice() {
  const q = keyword.value.trim()
  if (!q) {
    uni.showToast({ title: '请输入关键字', icon: 'none' })
    return
  }
  try {
    const list = await http.get<Row[]>('/repair/workorder/devices/lookup', { q })
    candidates.value = list || []
    if (candidates.value.length === 1) device.value = candidates.value[0]
    if (!candidates.value.length) uni.showToast({ title: '未找到设备', icon: 'none' })
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '搜索失败', icon: 'none' })
  }
}

async function save(unbind: boolean) {
  if (!tag.value) return
  if (!unbind && !device.value) {
    uni.showToast({ title: '请先选择设备', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await http.post('/power/tag', {
      id: tag.value.id,
      tag_code: tag.value.tag_code,
      tag_name: tag.value.tag_name,
      station_id: tag.value.station_id,
      rated_power: tag.value.rated_power,
      install_date: tag.value.install_date,
      is_active: tag.value.is_active ?? true,
      remark: tag.value.remark,
      device_id: unbind ? null : (device.value?.id ?? device.value?.device_id),
      client: 'mp'
    })
    uni.showToast({ title: unbind ? '已解绑' : '绑定已更新' })
    await loadTag()
    if (unbind) device.value = null
  } catch (e: unknown) {
    const msg = (e as Error).message || '请核对标签信息，可到 Web 端维护信息'
    uni.showToast({ title: msg, icon: 'none', duration: 3000 })
  } finally {
    saving.value = false
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
.head {
  margin-bottom: 16rpx;
}
.section-label {
  margin: 8rpx 8rpx 16rpx;
  font-size: 24rpx;
  color: $meis-text-muted;
}
.card {
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-lg;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.card.selected {
  border-color: $meis-primary;
  background: rgba(37, 99, 235, 0.06);
}
.title {
  font-size: 30rpx;
  font-weight: 600;
}
.row-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.input {
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-md;
  padding: 16rpx 20rpx;
  margin-bottom: 16rpx;
  font-size: 28rpx;
}
.mt {
  margin-top: 16rpx;
}
</style>
