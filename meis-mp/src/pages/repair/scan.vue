<template>
  <view class="repair-page">
    <view class="section">
      <text class="section-title">设备</text>
      <view v-if="!readonly" class="row-actions">
        <button class="btn" type="primary" size="mini" @click="scan">扫一扫</button>
        <button class="btn" size="mini" @click="lookupManual">搜索设备</button>
      </view>
      <view class="field">
        <text class="label">设备（编码/名称/首拼）</text>
        <input
          v-model="deviceCode"
          class="input"
          :disabled="readonly"
          placeholder="扫码或输入名称/首拼/编码"
          @confirm="lookupManual"
        />
      </view>
      <view v-if="candidates.length" class="candidates">
        <text class="section-title">检索到 {{ candidates.length }} 台，请选择</text>
        <view
          v-for="(c, idx) in candidates"
          :key="String(c.id || idx)"
          class="candidate"
          @click="selectDevice(c)"
        >
          <text class="d-name">{{ c.device_name }}</text>
          <text class="d-meta">{{ c.device_code }} · {{ c.dept_name || '—' }} · {{ c.pinyin_code || '' }}</text>
        </view>
      </view>
      <view v-if="device" class="device-box">
        <text class="d-name">{{ device.device_name }}</text>
        <text class="d-meta">{{ device.dept_name || '—' }} · {{ statusLabel(device.device_status) }}</text>
        <text v-if="device.can_report === false" class="warn">{{ device.cannot_report_reason || '当前不可报修' }}</text>
        <text v-else class="ok">可报修</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">故障信息</text>
      <view class="field">
        <text class="label">紧急程度</text>
        <picker
          :disabled="readonly"
          :range="urgencyLabels"
          :value="urgencyIndex"
          @change="onUrgencyChange"
        >
          <view class="picker">{{ urgencyLabels[urgencyIndex] }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">故障类型</text>
        <picker
          :disabled="readonly || !faultTypes.length"
          :range="faultTypeLabels"
          :value="faultTypeIndex"
          @change="onFaultTypeChange"
        >
          <view class="picker">{{ faultTypeLabels[faultTypeIndex] || '请选择（可选）' }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">故障描述</text>
        <textarea
          v-model="faultDescription"
          class="textarea"
          :disabled="readonly"
          placeholder="请描述故障现象"
          maxlength="500"
        />
      </view>
      <view class="field">
        <text class="label">备注</text>
        <textarea
          v-model="remark"
          class="textarea remark"
          :disabled="readonly"
          placeholder="可选"
          maxlength="200"
        />
      </view>
      <view class="field">
        <text class="label">故障图片（最多 3 张）</text>
        <view class="photos">
          <view v-for="(u, i) in photos" :key="u + i" class="thumb-wrap">
            <image :src="u" class="thumb" mode="aspectFill" @click="preview(i)" />
            <text v-if="!readonly" class="thumb-del" @click.stop="photos.splice(i, 1)">×</text>
          </view>
          <button v-if="!readonly && photos.length < 3" size="mini" @click="addPhoto">拍照/相册</button>
        </view>
      </view>
    </view>

    <view v-if="!readonly" class="actions">
      <button type="primary" :loading="submitting" :disabled="!canSave" @click="saveAndAskSubmit">
        {{ workorderId ? '保存草稿' : '保存' }}
      </button>
    </view>
    <view v-else class="hint-ro">非草稿状态，仅可查看</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { chooseAndUploadImage } from '@/api/upload'
import { useAuthStore } from '@/stores/auth'
import { requestSubscribe } from '@/utils/subscribe'

interface DeviceInfo {
  id: string
  device_code?: string
  device_name?: string
  pinyin_code?: string
  dept_id?: string
  dept_name?: string
  device_status?: string
  can_report?: boolean
  cannot_report_reason?: string
}

const auth = useAuthStore()
const deviceCode = ref('')
const device = ref<DeviceInfo | null>(null)
const candidates = ref<DeviceInfo[]>([])
const faultDescription = ref('')
const remark = ref('')
const photos = ref<string[]>([])
const workorderId = ref<string | null>(null)
const readonly = ref(false)
const submitting = ref(false)
const looking = ref(false)

const urgencyOptions = [
  { value: 'normal', label: '一般' },
  { value: 'urgent', label: '紧急' },
  { value: 'critical', label: '特急' }
]
const urgencyLabels = urgencyOptions.map((o) => o.label)
const urgencyIndex = ref(0)

const faultTypes = ref<Record<string, unknown>[]>([])
/** 0 = 未选择；其余对齐 faultTypes[i-1] */
const faultTypeIndex = ref(0)
const faultTypeLabels = computed(() => [
  '未选择',
  ...faultTypes.value.map((t) => String(t.fault_name || t.fault_code || t.id || ''))
])

function onFaultTypeChange(e: { detail: { value: string } }) {
  faultTypeIndex.value = Number(e.detail.value)
}

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

function onUrgencyChange(e: { detail: { value: string } }) {
  urgencyIndex.value = Number(e.detail.value)
}

const canSave = computed(() => {
  return !!device.value?.id
    && device.value.can_report !== false
    && !!faultDescription.value.trim()
    && !submitting.value
    && !readonly.value
})

async function loadFaultTypes() {
  try {
    const raw = await http.get<unknown>('/repair/fault_type_dict/list', { limit: 200 })
    const list = Array.isArray(raw) ? raw : (raw as { records?: unknown[] })?.records || []
    faultTypes.value = list.map((e) => ({ ...(e as object) })) as Record<string, unknown>[]
  } catch {
    faultTypes.value = []
  }
}

async function loadWorkorder(id: string) {
  uni.showLoading({ title: '加载中' })
  try {
    const m = await http.get<Record<string, unknown>>(`/repair/workorder/${id}`)
    workorderId.value = String(m.id || id)
    const st = String(m.status || '')
    readonly.value = st !== 'draft'
    deviceCode.value = String(m.device_code || '')
    faultDescription.value = String(m.fault_description || '')
    remark.value = String(m.remark || '')
    const urg = String(m.urgency_level || 'normal')
    urgencyIndex.value = Math.max(0, urgencyOptions.findIndex((o) => o.value === urg))
    const ftId = m.fault_type_id != null ? String(m.fault_type_id) : ''
    if (ftId && faultTypes.value.length) {
      const idx = faultTypes.value.findIndex((t) => String(t.id) === ftId)
      faultTypeIndex.value = idx >= 0 ? idx + 1 : 0
    } else {
      faultTypeIndex.value = 0
    }
    const fp = m.fault_photos
    photos.value = Array.isArray(fp) ? fp.map((e) => String(e)).filter(Boolean) : []
    if (m.device_id) {
      device.value = {
        id: String(m.device_id),
        device_code: String(m.device_code || ''),
        device_name: String(m.device_name || ''),
        dept_id: m.report_dept_id != null ? String(m.report_dept_id) : undefined,
        can_report: true
      }
    }
    uni.setNavigationBarTitle({ title: readonly.value ? '报修详情' : '编辑草稿' })
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function fetchByQuery(code: string) {
  const c = code.trim()
  if (!c) {
    uni.showToast({ title: '请输入设备编码、名称或首拼', icon: 'none' })
    return
  }
  looking.value = true
  candidates.value = []
  device.value = null
  try {
    const list = await http.get<DeviceInfo[]>('/repair/workorder/devices/lookup', { q: c })
    const rows = Array.isArray(list) ? list : []
    if (!rows.length) {
      uni.showToast({ title: '没有检索到匹配设备', icon: 'none' })
      return
    }
    if (rows.length === 1) {
      selectDevice(rows[0])
      return
    }
    candidates.value = rows
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '查找失败', icon: 'none' })
  } finally {
    looking.value = false
  }
}

function selectDevice(row: DeviceInfo) {
  device.value = row
  candidates.value = []
  deviceCode.value = String(row.device_code || deviceCode.value)
  if (row.can_report === false) {
    uni.showToast({ title: row.cannot_report_reason || '不可报修', icon: 'none' })
  }
}

function lookupManual() {
  if (readonly.value) return
  fetchByQuery(deviceCode.value)
}

function scan() {
  if (readonly.value) return
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const raw = (res.result || '').trim()
      if (!raw) {
        uni.showToast({ title: '未识别到内容', icon: 'none' })
        return
      }
      deviceCode.value = raw
      fetchByQuery(raw)
    },
    fail: () => {
      uni.showToast({ title: '扫码取消或失败', icon: 'none' })
    }
  })
}

function preview(index: number) {
  uni.previewImage({ urls: photos.value, current: photos.value[index] })
}

async function addPhoto() {
  if (photos.value.length >= 3) {
    uni.showToast({ title: '故障图片最多 3 张', icon: 'none' })
    return
  }
  try {
    const urls = await chooseAndUploadImage(Math.min(3 - photos.value.length, 3))
    photos.value = [...photos.value, ...urls].slice(0, 3)
  } catch (e: unknown) {
    if (e instanceof Error && e.message.includes('取消')) return
    uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
  }
}

function buildBody() {
  const ft = faultTypeIndex.value > 0 ? faultTypes.value[faultTypeIndex.value - 1] : null
  return {
    device_id: device.value!.id,
    device_code: device.value!.device_code,
    device_name: device.value!.device_name,
    reporter_id: auth.user?.userId,
    report_dept_id: device.value!.dept_id,
    report_method: 'miniprogram',
    fault_description: faultDescription.value.trim(),
    urgency_level: urgencyOptions[urgencyIndex.value].value,
    fault_type_id: ft?.id ?? null,
    remark: remark.value.trim() || null,
    fault_photos: photos.value
  }
}

async function saveAndAskSubmit() {
  if (!canSave.value || !device.value) return
  submitting.value = true
  try {
    const body = buildBody()
    const saved = workorderId.value
      ? await http.put<Record<string, unknown>>(`/repair/workorder/${workorderId.value}`, body)
      : await http.post<Record<string, unknown>>('/repair/workorder', body)
    const id = String(saved?.id || workorderId.value || '')
    if (!id) throw new Error('保存失败')
    workorderId.value = id

    const doSubmit = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: '是否提交',
        content: '草稿已保存，是否立即提交报修？',
        confirmText: '是',
        cancelText: '否',
        success: (res) => resolve(!!res.confirm)
      })
    })
    if (doSubmit) {
      const done = await http.post<{ wo_no?: string }>(`/repair/workorder/${id}/submit`)
      await requestSubscribe('repair_submit')
      uni.showModal({
        title: '报修成功',
        content: `工单号：${done?.wo_no || id}`,
        showCancel: false,
        success: () => {
          uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/repair/mine' }) })
        }
      })
    } else {
      uni.showToast({ title: '草稿已保存', icon: 'success' })
      uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/repair/mine' }) })
    }
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '保存失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad(async (query) => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  await loadFaultTypes()
  const id = query?.id ? String(query.id) : ''
  if (id) {
    await loadWorkorder(id)
    return
  }
  const code = query?.code ? decodeURIComponent(String(query.code)) : ''
  if (code) {
    deviceCode.value = code
    fetchByQuery(code)
  }
})
</script>

<style scoped>
.repair-page {
  padding: 24rpx 24rpx 60rpx;
}
.section {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 24rpx;
}
.section-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.row-actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.btn {
  margin: 0;
}
.field {
  margin-bottom: 20rpx;
}
.label {
  display: block;
  margin-bottom: 10rpx;
  font-size: 24rpx;
  color: #667085;
}
.input,
.picker,
.textarea {
  width: 100%;
  box-sizing: border-box;
  background: #f3f6fa;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
}
.textarea {
  min-height: 180rpx;
  width: auto;
}
.textarea.remark {
  min-height: 100rpx;
}
.candidates {
  margin-top: 12rpx;
}
.candidate {
  padding: 20rpx;
  margin-bottom: 12rpx;
  background: #f7fbff;
  border-radius: 12rpx;
  border: 1px solid #d6e6f8;
}
.device-box {
  margin-top: 8rpx;
  padding: 20rpx;
  background: #f7fbff;
  border-radius: 12rpx;
  border: 1px solid #d6e6f8;
}
.d-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
}
.d-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
.warn {
  display: block;
  margin-top: 10rpx;
  color: #d92d20;
  font-size: 24rpx;
}
.ok {
  display: block;
  margin-top: 10rpx;
  color: #039855;
  font-size: 24rpx;
}
.photos {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  align-items: center;
}
.thumb-wrap {
  position: relative;
  width: 120rpx;
  height: 120rpx;
}
.thumb {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  background: #eee;
}
.thumb-del {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  width: 36rpx;
  height: 36rpx;
  line-height: 32rpx;
  text-align: center;
  border-radius: 50%;
  background: #d92d20;
  color: #fff;
  font-size: 28rpx;
}
.actions button {
  margin: 0;
  background: #1a5fb4;
  color: #fff;
  border-radius: 12rpx;
}
.actions button[disabled] {
  opacity: 0.5;
}
.hint-ro {
  text-align: center;
  color: #98a2b3;
  font-size: 26rpx;
  padding: 24rpx;
}
</style>
