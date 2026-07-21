<template>
  <view class="hub">
    <view class="toolbar">
      <button type="primary" size="mini" @click="scanAndExecute">扫码执行</button>
      <button size="mini" @click="manualCode">手输编码</button>
      <button size="mini" @click="loadDue">刷新到期</button>
    </view>

    <view class="section-label">到期计划明细</view>
    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!dueItems.length" class="empty">暂无到期项</view>
    <view
      v-for="(row, idx) in dueItems"
      :key="String(row.id || idx)"
      class="due-item"
      @click="onDueTap(row)"
    >
      <text class="due-title">{{ row.device_name || row.device_code || '设备' }}</text>
      <text class="due-meta">
        {{ row.plan_no || '' }} · 到期 {{ formatDate(row.next_due_date) }}
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { http } from '@/api/http'
import { OPS_MODULES, type OpsModule, type OpsModuleConfig } from '@/config/ops'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const moduleKey = ref<OpsModule>('maintain')
const cfg = ref<OpsModuleConfig>(OPS_MODULES.maintain)
const dueItems = ref<Record<string, unknown>[]>([])
const loading = ref(false)

onLoad((query) => {
  const m = (query?.module as OpsModule) || 'maintain'
  moduleKey.value = m in OPS_MODULES ? m : 'maintain'
  cfg.value = OPS_MODULES[moduleKey.value]
  uni.setNavigationBarTitle({ title: cfg.value.title })
})

onShow(() => {
  auth.restore()
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loadDue()
})

function formatDate(v: unknown) {
  if (!v) return '—'
  return String(v).slice(0, 10)
}

async function loadDue() {
  loading.value = true
  try {
    const raw = await http.get<unknown>(cfg.value.planDuePath)
    const list = Array.isArray(raw) ? raw : (raw as { records?: unknown[] })?.records || []
    dueItems.value = list.map((e) => ({ ...(e as object) })) as Record<string, unknown>[]
  } catch {
    dueItems.value = []
  } finally {
    loading.value = false
  }
}

function scanAndExecute() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      const code = (res.result || '').trim()
      if (!code) {
        uni.showToast({ title: '未识别到内容', icon: 'none' })
        return
      }
      openByCode(code)
    },
    fail: () => uni.showToast({ title: '扫码取消或失败', icon: 'none' })
  })
}

function manualCode() {
  uni.showModal({
    title: '设备编码',
    editable: true,
    placeholderText: '输入设备编码',
    success: (res) => {
      if (res.confirm && res.content) openByCode(res.content.trim())
    }
  })
}

async function openByCode(code: string) {
  uni.showLoading({ title: '查找设备' })
  try {
    const list = await http.get<Record<string, unknown>[]>('/repair/workorder/devices/lookup', {
      deviceCode: code
    })
    const devices = Array.isArray(list) ? list : []
    if (!devices.length) {
      uni.showToast({ title: '未找到设备', icon: 'none' })
      return
    }
    if (devices.length === 1) {
      await openDeviceTasks(devices[0])
      return
    }
    const names = devices.map(
      (d) => `${d.device_name || ''} (${d.device_code || ''})`
    )
    uni.showActionSheet({
      itemList: names.slice(0, 6),
      success: async (r) => {
        await openDeviceTasks(devices[r.tapIndex])
      }
    })
  } catch (e: unknown) {
    uni.showToast({ title: e instanceof Error ? e.message : '查找失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function onDueTap(row: Record<string, unknown>) {
  if (row.device_id || row.device_code) {
    const device = {
      id: row.device_id,
      device_code: row.device_code,
      device_name: row.device_name
    }
    await openDeviceTasks(device)
  }
}

async function openDeviceTasks(device: Record<string, unknown>) {
  const deviceId = device.id?.toString()
  if (!deviceId) return
  uni.showLoading({ title: '加载任务' })
  try {
    const raw = await http.get<Record<string, unknown>[]>(
      `${cfg.value.executionBase}/by-device/${deviceId}`,
      { openOnly: true }
    )
    const items = Array.isArray(raw) ? raw : []
    uni.hideLoading()
    if (!items.length) {
      uni.showModal({
        title: '无待执行任务',
        content: `设备 ${device.device_name || ''} 暂无未审核执行单，是否直开？`,
        success: (res) => {
          if (res.confirm) createAdHoc(device)
        }
      })
      return
    }
    const labels = items.map(
      (it) => `${it.execution_no || ''} · ${it.execution_status || ''}`
    )
    const extra = '＋ 无计划直开'
    uni.showActionSheet({
      itemList: [...labels.slice(0, 5), extra],
      success: (r) => {
        if (r.tapIndex === labels.slice(0, 5).length || r.tapIndex >= items.length) {
          createAdHoc(device)
          return
        }
        const it = items[r.tapIndex]
        const execId = it.execution_id?.toString()
        const itemId = it.id?.toString()
        if (execId && itemId) openDetail(execId, itemId)
      }
    })
  } catch (e: unknown) {
    uni.hideLoading()
    uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' })
  }
}

async function createAdHoc(device: Record<string, unknown>) {
  let templates: Record<string, unknown>[] = []
  try {
    const raw = await http.get<unknown>(cfg.value.templateListPath, { limit: 50 })
    const list = Array.isArray(raw) ? raw : (raw as { records?: unknown[] })?.records || []
    templates = list.map((e) => ({ ...(e as object) })) as Record<string, unknown>[]
  } catch {
    templates = []
  }
  if (!templates.length) {
    uni.showToast({ title: '暂无可用模板', icon: 'none' })
    return
  }
  const names = templates.map((t) => String(t.template_name || t.id)).slice(0, 6)
  uni.showActionSheet({
    itemList: names,
    success: (r) => {
      const selected = templates[r.tapIndex]
      uni.showModal({
        title: cfg.value.levelLabel,
        editable: true,
        placeholderText: `请填写${cfg.value.levelLabel}`,
        success: async (m) => {
          if (!m.confirm) return
          const level = (m.content || '').trim()
          if (!level) {
            uni.showToast({ title: `请填写${cfg.value.levelLabel}`, icon: 'none' })
            return
          }
          try {
            uni.showLoading({ title: '创建中' })
            const body: Record<string, unknown> = {
              template_id: selected.id,
              template_name: selected.template_name,
              [cfg.value.levelField]: level,
              device_id: device.id,
              client: 'mp'
            }
            const created = await http.post<Record<string, unknown>>(
              `${cfg.value.executionBase}/ad-hoc`,
              body
            )
            const execId = created?.id?.toString()
            if (!execId) throw new Error('创建失败')
            const detail = await http.get<Record<string, unknown>>(
              `${cfg.value.executionBase}/${execId}`
            )
            const items = Array.isArray(detail?.items) ? (detail.items as Record<string, unknown>[]) : []
            let itemId = items.find((it) => String(it.device_id) === String(device.id))?.id
            if (!itemId && items[0]) itemId = items[0].id
            if (itemId) openDetail(execId, String(itemId))
            loadDue()
          } catch (e: unknown) {
            uni.showToast({ title: e instanceof Error ? e.message : '创建失败', icon: 'none' })
          } finally {
            uni.hideLoading()
          }
        }
      })
    }
  })
}

function openDetail(execId: string, itemId: string) {
  uni.navigateTo({
    url: `/pages/ops/detail?module=${moduleKey.value}&executionId=${execId}&itemId=${itemId}`
  })
}
</script>

<style scoped>
.hub {
  min-height: 100vh;
  padding: 24rpx 24rpx 48rpx;
  background: #f5f7fa;
}
.toolbar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 28rpx;
  flex-wrap: wrap;
}
.toolbar button {
  margin: 0;
}
.section-label {
  font-size: 24rpx;
  color: #98a2b3;
  margin: 0 8rpx 16rpx;
}
.empty {
  text-align: center;
  color: #98a2b3;
  padding: 48rpx;
  font-size: 26rpx;
}
.due-item {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  border: 1px solid rgba(26, 95, 180, 0.08);
}
.due-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2a37;
}
.due-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #667085;
}
</style>
