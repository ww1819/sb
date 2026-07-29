<template>
  <view class="page">
    <view class="field">
      <text class="label">基站编码</text>
      <input class="input" v-model="form.station_code" :disabled="lockCode" placeholder="基站编码" />
    </view>
    <view class="field">
      <text class="label">基站名称</text>
      <input class="input" v-model="form.station_name" placeholder="基站名称" />
    </view>
    <view class="field">
      <text class="label">院区</text>
      <picker mode="selector" :range="campusLabels" :value="campusIndex" @change="onCampus">
        <text class="value">{{ form.campus_name || '未选择（点此选择）' }}</text>
      </picker>
      <text v-if="form.campus_id" class="clear" @click.stop="clearCampus">清除</text>
    </view>
    <view class="field">
      <text class="label">安装位置</text>
      <input class="input" v-model="form.location" placeholder="可选" />
    </view>
    <view class="field">
      <text class="label">IP地址</text>
      <input class="input" v-model="form.ip_address" placeholder="可选" />
    </view>
    <view class="field">
      <text class="label">协议类型</text>
      <picker :range="protocols" :value="protocolIndex" @change="onProtocol">
        <text class="value">{{ form.protocol_type }}</text>
      </picker>
    </view>
    <view class="field">
      <text class="label">状态</text>
      <picker :range="statuses" :value="statusIndex" @change="onStatus">
        <text class="value">{{ form.status }}</text>
      </picker>
    </view>
    <view class="field row">
      <text class="label">启用</text>
      <switch :checked="form.is_active" @change="onActive" />
    </view>
    <view class="field col">
      <text class="label">备注</text>
      <textarea class="textarea" v-model="form.remark" />
    </view>
    <button type="primary" :loading="saving" @click="onSave">保存</button>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const lockCode = ref(false)
const stationId = ref<string | null>(null)
const campuses = ref<Record<string, unknown>[]>([])
const protocols = ['mqtt', 'modbus', 'http', 'other']
const statuses = ['online', 'offline', 'fault', 'maintenance']

const form = reactive({
  station_code: '',
  station_name: '',
  campus_id: null as string | null,
  campus_name: null as string | null,
  location: '',
  ip_address: '',
  protocol_type: 'mqtt',
  status: 'online',
  is_active: true,
  remark: ''
})

const campusLabels = computed(() =>
  campuses.value.map((c) => `${c.campus_code || ''} · ${c.campus_name || ''}`)
)
const campusIndex = computed(() => {
  if (!form.campus_id) return 0
  const i = campuses.value.findIndex((c) => String(c.id) === form.campus_id)
  return i >= 0 ? i : 0
})
const protocolIndex = computed(() => Math.max(0, protocols.indexOf(form.protocol_type)))
const statusIndex = computed(() => Math.max(0, statuses.indexOf(form.status)))

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) uni.reLaunch({ url: '/pages/login/index' })
})

onLoad(async (q) => {
  await loadCampuses()
  if (q?.id) {
    stationId.value = String(q.id)
    lockCode.value = true
    await loadStation(String(q.id))
  }
})

async function loadCampuses() {
  try {
    const list = await http.get<Record<string, unknown>[]>('/system/campuses')
    campuses.value = Array.isArray(list) ? list : []
  } catch {
    /* ignore */
  }
}

async function loadStation(id: string) {
  try {
    const m = await http.get<Record<string, unknown>>(`/power/station/${id}`)
    form.station_code = String(m.station_code ?? '')
    form.station_name = String(m.station_name ?? '')
    form.campus_id = m.campus_id != null ? String(m.campus_id) : null
    form.campus_name = m.campus_name != null ? String(m.campus_name) : null
    form.location = m.location != null ? String(m.location) : ''
    form.ip_address = m.ip_address != null ? String(m.ip_address) : ''
    form.protocol_type = String(m.protocol_type ?? 'mqtt')
    form.status = String(m.status ?? 'online')
    form.is_active = m.is_active === true || m.is_active === 'true'
    form.remark = m.remark != null ? String(m.remark) : ''
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '加载失败', icon: 'none' })
  }
}

function onCampus(e: { detail: { value: string } }) {
  const c = campuses.value[Number(e.detail.value)]
  if (!c) return
  form.campus_id = String(c.id)
  form.campus_name = String(c.campus_name ?? '')
}

function clearCampus() {
  form.campus_id = null
  form.campus_name = null
}

function onProtocol(e: { detail: { value: string } }) {
  form.protocol_type = protocols[Number(e.detail.value)] || 'mqtt'
}

function onStatus(e: { detail: { value: string } }) {
  form.status = statuses[Number(e.detail.value)] || 'online'
}

function onActive(e: { detail: { value: boolean } }) {
  form.is_active = e.detail.value
}

async function onSave() {
  const code = form.station_code.trim()
  const name = form.station_name.trim()
  if (!code) {
    uni.showToast({ title: '请填写基站编码', icon: 'none' })
    return
  }
  if (!name) {
    uni.showToast({ title: '请填写基站名称', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await http.post('/power/station', {
      id: stationId.value || undefined,
      station_code: code,
      station_name: name,
      campus_id: form.campus_id,
      location: form.location || null,
      ip_address: form.ip_address || null,
      protocol_type: form.protocol_type,
      status: form.status,
      is_active: form.is_active,
      remark: form.remark || null,
      client: 'mp'
    })
    uni.showToast({ title: '保存成功' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e: unknown) {
    uni.showToast({
      title: (e as Error).message || '请核对基站信息，可到 Web 端维护',
      icon: 'none',
      duration: 3000
    })
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
.field {
  background: #fff;
  border: 1px solid $meis-border;
  border-radius: $meis-radius-md;
  padding: 20rpx 24rpx;
  margin-bottom: 16rpx;
}
.field.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.label {
  display: block;
  font-size: 24rpx;
  color: $meis-text-muted;
  margin-bottom: 8rpx;
}
.input,
.value,
.textarea {
  font-size: 28rpx;
  width: 100%;
  color: $meis-text-primary;
}
.clear {
  display: inline-block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $meis-primary;
}
.textarea {
  min-height: 120rpx;
}
</style>
