<template>
  <view class="page">
    <view class="field">
      <text class="label">标签编码</text>
      <input
        class="input"
        v-model="form.tag_code"
        :disabled="lockCode"
        placeholder="标签编码"
      />
    </view>
    <view class="field">
      <text class="label">标签名称</text>
      <input class="input" v-model="form.tag_name" placeholder="便于识别，勿与编码相同" />
    </view>
    <view class="field">
      <text class="label">所属基站</text>
      <picker
        mode="selector"
        :range="stationLabels"
        :value="stationIndex"
        @change="onStationPick"
      >
        <text class="value">{{ form.station_name || '未选择（点此选择）' }}</text>
      </picker>
      <text v-if="form.station_id" class="clear" @click.stop="clearStation">清除</text>
    </view>
    <view class="field">
      <text class="label">额定功率(W)</text>
      <input class="input" v-model="form.rated_power" type="digit" placeholder="可选" />
    </view>
    <view class="field">
      <text class="label">安装日期</text>
      <picker mode="date" :value="form.install_date || ''" @change="onDate">
        <text class="value">{{ form.install_date || '未设置' }}</text>
      </picker>
    </view>
    <view class="field row">
      <text class="label">启用</text>
      <switch :checked="form.is_active" @change="onActive" />
    </view>
    <view class="field col">
      <text class="label">备注</text>
      <textarea class="textarea" v-model="form.remark" placeholder="可选" />
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
const tagId = ref<string | null>(null)
const stations = ref<Record<string, unknown>[]>([])
const form = reactive({
  tag_code: '',
  tag_name: '',
  station_id: '' as string | null,
  station_name: '' as string | null,
  rated_power: '',
  install_date: '' as string | null,
  is_active: true,
  remark: '',
  device_id: null as string | null
})

const stationLabels = computed(() =>
  stations.value.map((s) => `${s.station_code || ''} · ${s.station_name || ''}`)
)
const stationIndex = computed(() => {
  if (!form.station_id) return 0
  const i = stations.value.findIndex((s) => String(s.id) === form.station_id)
  return i >= 0 ? i : 0
})

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) uni.reLaunch({ url: '/pages/login/index' })
})

onLoad(async (q) => {
  if (q?.tagCode) {
    form.tag_code = decodeURIComponent(String(q.tagCode))
  }
  if (q?.lockCode === '1') lockCode.value = true
  await loadStations()
  if (q?.id) {
    tagId.value = String(q.id)
    lockCode.value = true
    await loadTag(String(q.id))
  }
})

async function loadStations() {
  try {
    const page = await http.get<{ records?: Record<string, unknown>[] }>('/power/station/page', {
      page: 1,
      size: 200,
      activeOnly: true
    })
    stations.value = page.records ?? []
  } catch {
    /* ignore */
  }
}

async function loadTag(id: string) {
  try {
    const m = await http.get<Record<string, unknown>>(`/power/tag/${id}`)
    form.tag_code = String(m.tag_code ?? '')
    form.tag_name = String(m.tag_name ?? '')
    form.station_id = m.station_id != null ? String(m.station_id) : null
    form.station_name = m.station_name != null ? String(m.station_name) : null
    form.rated_power = m.rated_power != null ? String(m.rated_power) : ''
    form.install_date = m.install_date ? String(m.install_date).slice(0, 10) : null
    form.is_active = m.is_active === true || m.is_active === 'true'
    form.remark = m.remark != null ? String(m.remark) : ''
    form.device_id = m.device_id != null ? String(m.device_id) : null
  } catch (e: unknown) {
    uni.showToast({ title: (e as Error).message || '加载失败', icon: 'none' })
  }
}

function onStationPick(e: { detail: { value: string } }) {
  const idx = Number(e.detail.value)
  const s = stations.value[idx]
  if (!s) return
  form.station_id = String(s.id)
  form.station_name = String(s.station_name ?? '')
}

function clearStation() {
  form.station_id = null
  form.station_name = null
}

function onDate(e: { detail: { value: string } }) {
  form.install_date = e.detail.value
}

function onActive(e: { detail: { value: boolean } }) {
  form.is_active = e.detail.value
}

async function onSave() {
  const code = form.tag_code.trim()
  const name = form.tag_name.trim()
  if (!code) {
    uni.showToast({ title: '请填写标签编码', icon: 'none' })
    return
  }
  if (!name) {
    uni.showToast({ title: '请填写标签名称', icon: 'none' })
    return
  }
  if (name === code) {
    uni.showToast({ title: '标签名称请勿与编码相同', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await http.post('/power/tag', {
      id: tagId.value || undefined,
      tag_code: code,
      tag_name: name,
      station_id: form.station_id,
      device_id: form.device_id,
      rated_power: form.rated_power ? Number(form.rated_power) : null,
      install_date: form.install_date || null,
      is_active: form.is_active,
      remark: form.remark || null,
      client: 'mp'
    })
    uni.showToast({ title: '保存成功' })
    setTimeout(() => uni.navigateBack(), 500)
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
.field.col .label {
  margin-bottom: 12rpx;
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
  color: $meis-text-primary;
  width: 100%;
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
