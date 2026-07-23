<template>
  <view class="detail">
    <view v-if="loading" class="empty">加载中…</view>
    <template v-else>
      <view class="head">
        <text class="no">{{ exec?.execution_no || '执行明细' }}</text>
        <text class="meta">{{ item?.device_name }} · {{ item?.device_code }}</text>
        <text class="status">状态：{{ exec?.status || '—' }} / 明细：{{ item?.status || '—' }}</text>
        <text class="channels">
          制单途径：{{ channelLabel(exec?.create_channel) }} · 审核途径：{{ channelLabel(exec?.audit_channel) }}
        </text>
        <text class="channels">
          审核人：{{ blankDash(exec?.auditor_name) }} · 审核时间：{{ blankDash(exec?.audited_at) }}
        </text>
        <text class="channels">
          执行途径：{{ channelLabel(item?.execution_channel) }} · 确认途径：{{ channelLabel(item?.confirm_channel) }}
        </text>
        <text v-if="String(item?.status) === 'confirmed'" class="locked">已确认，不可再修改</text>
      </view>

      <view v-for="(r, idx) in results" :key="r.id || idx" class="card">
        <text class="item-name">{{ r.name }}</text>
        <text v-if="r.content" class="item-content">{{ r.content }}</text>
        <view class="status-row">
          <text
            v-for="s in statusOpts"
            :key="s.value"
            class="chip"
            :class="{ active: r.status === s.value, fail: s.value === 'fail' && r.status === s.value }"
            @click="editable && (r.status = s.value)"
          >
            {{ s.label }}
          </text>
        </view>
        <input
          v-if="editable"
          v-model="r.value"
          class="input"
          placeholder="结果值（可选）"
        />
        <textarea
          v-if="editable"
          v-model="r.remark"
          class="textarea"
          placeholder="备注"
        />
        <view class="photos">
          <image
            v-for="(u, i) in r.photos"
            :key="u + i"
            :src="u"
            class="thumb"
            mode="aspectFill"
            @click="preview(r.photos, i)"
          />
          <button v-if="editable && r.photos.length < 6" size="mini" @click="addResultPhoto(r)">
            拍照
          </button>
        </view>
      </view>

      <view class="card">
        <text class="item-name">设备项附件</text>
        <view class="photos">
          <image
            v-for="(u, i) in itemPhotos"
            :key="u + i"
            :src="u"
            class="thumb"
            mode="aspectFill"
            @click="preview(itemPhotos, i)"
          />
          <button v-if="editable && itemPhotos.length < 6" size="mini" @click="addItemPhoto">
            拍照
          </button>
        </view>
        <view class="sign-row">
          <text class="label">签名</text>
          <image v-if="signatureUrl" :src="signatureUrl" class="sign-img" mode="aspectFit" />
          <button v-if="editable" size="mini" @click="openSignature">手写签名</button>
        </view>
      </view>

      <view v-if="editable || canConfirmItem" class="actions">
        <button v-if="editable" type="primary" :loading="saving" @click="complete">
          {{ completeLabel }}
        </button>
        <button v-if="canConfirmItem" :loading="saving" @click="confirmItem">确认本设备项</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { chooseAndUploadImage } from '@/api/upload'
import { OPS_MODULES, type OpsModule, type OpsModuleConfig } from '@/config/ops'
import { useAuthStore } from '@/stores/auth'
import { requestSubscribe } from '@/utils/subscribe'

interface ResultRow {
  id: string
  name: string
  content: string
  status: string
  value: string
  remark: string
  photos: string[]
  rowVersion: number
}

const auth = useAuthStore()
const cfg = ref<OpsModuleConfig>(OPS_MODULES.maintain)
const executionId = ref('')
const itemId = ref('')
const exec = ref<Record<string, unknown> | null>(null)
const item = ref<Record<string, unknown> | null>(null)
const results = ref<ResultRow[]>([])
const itemPhotos = ref<string[]>([])
const signatureUrl = ref<string | undefined>()
const loading = ref(true)
const saving = ref(false)

const statusOpts = [
  { value: 'pass', label: '合格' },
  { value: 'fail', label: '异常' },
  { value: 'na', label: '不适用' },
  { value: 'pending', label: '待检' }
]

const editable = computed(() => {
  const headerOk = ['draft', 'in_progress', 'pending'].includes(String(exec.value?.status || ''))
  const itemSt = String(item.value?.status || '')
  return headerOk && itemSt !== 'confirmed'
})

const canConfirmItem = computed(() => {
  const headerOk = ['draft', 'in_progress', 'pending'].includes(String(exec.value?.status || ''))
  return headerOk && String(item.value?.status || '') !== 'confirmed'
})

const completeLabel = computed(() =>
  String(item.value?.status || '') === 'completed' ? '保存修改' : '完成本设备项'
)

function channelLabel(raw: unknown) {
  const v = String(raw ?? '').trim()
  if (!v) return '—'
  const map: Record<string, string> = { web: 'Web', app: 'App', mp: '小程序' }
  return map[v] || v
}

function blankDash(raw: unknown) {
  const v = String(raw ?? '').trim()
  return v || '—'
}

onLoad((query) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  const m = (query?.module as OpsModule) || 'maintain'
  cfg.value = OPS_MODULES[m in OPS_MODULES ? m : 'maintain']
  executionId.value = String(query?.executionId || '')
  itemId.value = String(query?.itemId || '')
  uni.setNavigationBarTitle({ title: cfg.value.title })
  load()
})

function asUrlList(raw: unknown): string[] {
  if (Array.isArray(raw)) {
    return raw.map((e) => String(e)).filter((s) => s && s !== 'null')
  }
  return []
}

async function load() {
  loading.value = true
  try {
    const data = await http.get<Record<string, unknown>>(
      `${cfg.value.executionBase}/${executionId.value}`
    )
    const items = Array.isArray(data?.items) ? (data.items as Record<string, unknown>[]) : []
    const found = items.find((it) => String(it.id) === itemId.value)
    if (!found) throw new Error('明细不存在')
    const rawResults = Array.isArray(found.results) ? (found.results as Record<string, unknown>[]) : []
    exec.value = data
    item.value = found
    itemPhotos.value = asUrlList(found.photos)
    signatureUrl.value = found.signature_url ? String(found.signature_url) : undefined
    results.value = rawResults.map((m) => ({
      id: String(m.id || ''),
      name: String(m.item_name || '检查项'),
      content: String(m.item_content || ''),
      status: String(m.result_status || 'pending'),
      value: String(m.result_value || ''),
      remark: String(m.remark || ''),
      photos: asUrlList(m.photos),
      rowVersion: Number(m.row_version || 1)
    }))
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function preview(urls: string[], index: number) {
  uni.previewImage({ urls, current: urls[index] })
}

async function addResultPhoto(r: ResultRow) {
  try {
    const urls = await chooseAndUploadImage(1)
    r.photos = [...r.photos, ...urls].slice(0, 6)
  } catch (e: unknown) {
    if (e instanceof Error && e.message.includes('取消')) return
    uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
  }
}

async function addItemPhoto() {
  try {
    const urls = await chooseAndUploadImage(1)
    itemPhotos.value = [...itemPhotos.value, ...urls].slice(0, 6)
  } catch (e: unknown) {
    if (e instanceof Error && e.message.includes('取消')) return
    uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
  }
}

async function pickSignature() {
  try {
    const urls = await chooseAndUploadImage(1)
    if (urls[0]) signatureUrl.value = urls[0]
  } catch (e: unknown) {
    if (e instanceof Error && e.message.includes('取消')) return
    uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
  }
}

function openSignature() {
  uni.navigateTo({
    url: '/pages/ops/signature',
    events: {
      signed: (url: string) => {
        if (url) signatureUrl.value = url
      }
    },
    fail: () => pickSignature()
  })
}

async function ensureStarted() {
  const st = String(exec.value?.status || '')
  if (st === 'draft' || st === 'pending') {
    await http.post(`${cfg.value.executionBase}/${executionId.value}/start`, { client: 'mp' })
  }
}

async function complete() {
  if (!editable.value) return
  const failWithoutPhoto = results.value.some((r) => r.status === 'fail' && r.photos.length === 0)
  if (failWithoutPhoto) {
    const cont = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: '异常项未拍照',
        content: '存在异常检查项尚未拍照，是否仍完成？',
        confirmText: '仍完成',
        cancelText: '去拍照',
        success: (res) => resolve(!!res.confirm)
      })
    })
    if (!cont) return
  }
  saving.value = true
  try {
    await ensureStarted()
    const hasFail = results.value.some((r) => r.status === 'fail')
    await http.post(`${cfg.value.executionBase}/item/${itemId.value}/complete`, {
      client: 'mp',
      overall_result: hasFail ? 'fail' : 'pass',
      photos: itemPhotos.value,
      signature_url: signatureUrl.value,
      results: results.value.map((r) => ({
        id: r.id,
        result_status: r.status === 'pending' ? 'pass' : r.status,
        result_value: r.value || (r.status === 'fail' ? '不合格' : '合格'),
        remark: r.remark,
        photos: r.photos
      }))
    })
    await requestSubscribe('ops_complete')
    uni.showToast({ title: '已完成本设备项', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function confirmItem() {
  if (!canConfirmItem.value) return
  const st = String(item.value?.status || '')
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '确认明细',
      content:
        st === 'completed'
          ? '确认该设备执行结果？'
          : '结果未填完也可确认，将自动记为已完成再确认。是否继续？',
      success: (res) => resolve(!!res.confirm)
    })
  })
  if (!ok) return
  saving.value = true
  try {
    await http.post(`${cfg.value.executionBase}/item/${itemId.value}/confirm`, { client: 'mp' })
    uni.showToast({ title: '已确认', icon: 'success' })
    await load()
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '确认失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.detail {
  min-height: 100vh;
  padding: 24rpx 24rpx 80rpx;
  background: #f5f7fa;
}
.empty {
  text-align: center;
  padding: 80rpx;
  color: #98a2b3;
}
.head {
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
.meta,
.status,
.channels {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.locked {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #d97706;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}
.item-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}
.item-content {
  display: block;
  font-size: 24rpx;
  color: #667085;
  margin-bottom: 16rpx;
}
.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 16rpx;
}
.chip {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: #f3f6fa;
  font-size: 24rpx;
  color: #667085;
}
.chip.active {
  background: #1a5fb4;
  color: #fff;
}
.chip.fail {
  background: #d92d20;
}
.input,
.textarea {
  width: 100%;
  box-sizing: border-box;
  background: #f3f6fa;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  margin-bottom: 12rpx;
}
.textarea {
  min-height: 100rpx;
}
.photos {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  align-items: center;
}
.thumb {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  background: #eee;
}
.sign-row {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-wrap: wrap;
}
.label {
  font-size: 26rpx;
  color: #667085;
}
.sign-img {
  width: 240rpx;
  height: 100rpx;
  background: #f3f6fa;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 12rpx;
}
.actions button {
  margin: 0;
}
</style>
