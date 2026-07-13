<template>
  <div class="device-label-panel">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="二维码载荷为设备编码（创建后不可修改），请勿使用名称、科室等可改字段。"
      class="device-label-panel__alert"
    />

    <div class="device-label-panel__preview">
      <div ref="printAreaRef" class="device-label-panel__card">
        <div class="device-label-panel__qr">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="设备二维码" />
          <el-empty v-else description="无设备编码，无法生成二维码" :image-size="64" />
        </div>
        <div class="device-label-panel__meta">
          <div><span>设备编码</span><strong>{{ deviceCode || '—' }}</strong></div>
          <div><span>设备名称</span><strong>{{ deviceName || '—' }}</strong></div>
        </div>
      </div>
      <div class="device-label-panel__actions" v-if="deviceId">
        <el-button type="primary" :disabled="!deviceCode" :loading="printing" @click="doPrint">打印标签</el-button>
        <el-button :disabled="!qrDataUrl" @click="browserPrint">浏览器打印预览</el-button>
      </div>
    </div>

    <div class="device-label-panel__history">
      <h4>打印记录</h4>
      <el-table :data="prints" border stripe size="small" max-height="280" v-loading="loading">
        <el-table-column prop="device_code" label="设备编码" min-width="120" />
        <el-table-column prop="printed_at" label="打印时间" min-width="160" />
        <el-table-column prop="template_code" label="模板" width="100" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <template #empty>
          <el-empty description="暂无打印记录" :image-size="56" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

const props = defineProps<{
  deviceId: string
  deviceCode: string
  deviceName?: string
}>()

const qrDataUrl = ref('')
const prints = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const printing = ref(false)
const printAreaRef = ref<HTMLElement | null>(null)

async function renderQr() {
  if (!props.deviceCode) {
    qrDataUrl.value = ''
    return
  }
  qrDataUrl.value = await QRCode.toDataURL(props.deviceCode, { width: 200, margin: 1 })
}

async function loadPrints() {
  if (!props.deviceId) {
    prints.value = []
    return
  }
  loading.value = true
  try {
    const { data } = await http.get(`/asset/device/${props.deviceId}/label`)
    prints.value = data.data?.prints ?? []
  } catch {
    prints.value = []
  } finally {
    loading.value = false
  }
}

async function doPrint() {
  if (!props.deviceId || !props.deviceCode) return
  printing.value = true
  try {
    await http.post(`/asset/device/${props.deviceId}/label/print`, { template_code: 'default' })
    ElMessage.success('已记录打印')
    await loadPrints()
    browserPrint()
  } catch {
    ElMessage.error('打印失败')
  } finally {
    printing.value = false
  }
}

function browserPrint() {
  const node = printAreaRef.value
  if (!node) return
  const win = window.open('', '_blank', 'width=480,height=640')
  if (!win) {
    ElMessage.warning('请允许弹出窗口以打印标签')
    return
  }
  win.document.write(`<!doctype html><html><head><title>资产标签</title>
    <style>
      body{font-family:sans-serif;padding:24px;text-align:center}
      img{width:200px;height:200px}
      .meta{margin-top:12px;font-size:14px;line-height:1.8}
      strong{display:block;font-size:18px;letter-spacing:0.02em}
    </style></head><body>${node.innerHTML}</body></html>`)
  win.document.close()
  win.focus()
  win.print()
}

watch(
  () => [props.deviceCode, props.deviceId],
  () => {
    renderQr()
    loadPrints()
  },
  { immediate: true }
)
</script>

<style scoped>
.device-label-panel__alert {
  margin-bottom: 16px;
}
.device-label-panel__preview {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.device-label-panel__card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px 20px;
  min-width: 240px;
  text-align: center;
  background: #fff;
}
.device-label-panel__qr img {
  width: 180px;
  height: 180px;
}
.device-label-panel__meta {
  margin-top: 12px;
  text-align: left;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: grid;
  gap: 6px;
}
.device-label-panel__meta strong {
  display: block;
  color: var(--el-text-color-primary);
  font-size: 15px;
}
.device-label-panel__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.device-label-panel__history h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
</style>
