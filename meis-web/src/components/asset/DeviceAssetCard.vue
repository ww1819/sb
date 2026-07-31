<template>
  <div class="device-asset-card-wrap">
    <div class="device-asset-card-wrap__toolbar">
      <el-button type="primary" :disabled="!deviceCode" :loading="printing" @click="doPrint">
        打印卡片
      </el-button>
      <el-button :disabled="!deviceId" :loading="historyLoading" @click="loadPrints">刷新打印记录</el-button>
      <el-button :disabled="!deviceCode || !qrDataUrl" @click="browserPrint">浏览器打印预览</el-button>
    </div>

    <div ref="printAreaRef" class="device-asset-card">
      <div class="device-asset-card__header">
        <div class="device-asset-card__org">{{ campusLabel }}</div>
        <div class="device-asset-card__title">医疗设备资产卡片</div>
      </div>

      <div class="device-asset-card__identity">
        <div class="device-asset-card__qr">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="资产二维码" />
          <div v-else class="device-asset-card__qr-empty">无编码</div>
          <span class="device-asset-card__qr-hint">扫码载荷：资产编码</span>
        </div>
        <div class="device-asset-card__id-meta">
          <div class="device-asset-card__field">
            <span class="device-asset-card__label">资产编码</span>
            <span class="device-asset-card__value mono">{{ display(model.device_code) }}</span>
          </div>
          <div class="device-asset-card__field">
            <span class="device-asset-card__label">资产名称</span>
            <span class="device-asset-card__value">{{ display(model.device_name) }}</span>
          </div>
        </div>
      </div>

      <section class="device-asset-card__section">
        <h4>基本信息</h4>
        <div class="device-asset-card__grid">
          <div v-for="item in basicItems" :key="item.label" class="device-asset-card__field">
            <span class="device-asset-card__label">{{ item.label }}</span>
            <span class="device-asset-card__value" :class="{ mono: item.mono }">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <section class="device-asset-card__section">
        <h4>归属与位置</h4>
        <div class="device-asset-card__grid">
          <div v-for="item in locationItems" :key="item.label" class="device-asset-card__field">
            <span class="device-asset-card__label">{{ item.label }}</span>
            <span class="device-asset-card__value">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <section class="device-asset-card__section">
        <h4>生产厂家与供应商</h4>
        <div class="device-asset-card__grid">
          <div v-for="item in vendorItems" :key="item.label" class="device-asset-card__field">
            <span class="device-asset-card__label">{{ item.label }}</span>
            <span class="device-asset-card__value mono">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <section class="device-asset-card__section">
        <h4>财务摘要</h4>
        <div class="device-asset-card__grid">
          <div v-for="item in financeItems" :key="item.label" class="device-asset-card__field">
            <span class="device-asset-card__label">{{ item.label }}</span>
            <span class="device-asset-card__value">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <section class="device-asset-card__sign">
        <div class="device-asset-card__sign-item">
          <span>使用科室确认</span>
          <span class="device-asset-card__sign-line" />
        </div>
        <div class="device-asset-card__sign-item">
          <span>设备科确认</span>
          <span class="device-asset-card__sign-line" />
        </div>
        <div class="device-asset-card__sign-item">
          <span>日期</span>
          <span class="device-asset-card__sign-line" />
        </div>
      </section>
    </div>

    <div class="device-asset-card-wrap__history">
      <h4>卡片打印记录</h4>
      <el-table v-loading="historyLoading" :data="cardPrints" border stripe size="small" max-height="280">
        <el-table-column prop="printed_at" label="打印时间" min-width="160" />
        <el-table-column prop="printed_by_name" label="打印人" min-width="100" />
        <el-table-column prop="template_code" label="模板" width="110" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <template #empty>
          <el-empty description="暂无卡片打印记录" :image-size="56" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { ensureRefLabelMap, resolveRefLabel } from '@/composables/useRefLabelMap'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  model: Record<string, unknown>
  deviceId?: string
}>()

const { loadDict, getCached } = useDict()
const qrDataUrl = ref('')
const printAreaRef = ref<HTMLElement | null>(null)
const printing = ref(false)
const historyLoading = ref(false)
const prints = ref<Record<string, unknown>[]>([])

const deviceCode = computed(() => {
  const c = props.model.device_code
  return c != null && String(c).trim() !== '' ? String(c).trim() : ''
})

const deviceId = computed(() => {
  if (props.deviceId && String(props.deviceId).trim()) return String(props.deviceId).trim()
  const id = props.model.id
  return id != null && String(id).trim() !== '' ? String(id).trim() : ''
})

const cardPrints = computed(() =>
  prints.value.filter((r) => String(r.template_code ?? '').toLowerCase() === 'asset_card')
)

const campusLabel = computed(() => {
  const name = props.model.campus_name
  if (name != null && String(name).trim()) return String(name).trim()
  const fromMap = resolveRefLabel('campus', props.model.campus_id)
  return fromMap || '医疗机构'
})

const statusLabel = computed(() => {
  const val = props.model.device_status
  if (val === null || val === undefined || val === '') return '-'
  const items = getCached('device_status')
  return items.find((i) => i.value === String(val))?.label ?? String(val)
})

const deptLabel = computed(() => {
  const name = props.model.dept_name
  if (name != null && String(name).trim()) return String(name).trim()
  return resolveRefLabel('department', props.model.dept_id) || '-'
})

const warehouseLabel = computed(() => {
  const name = props.model.warehouse_name
  if (name != null && String(name).trim()) return String(name).trim()
  return resolveRefLabel('warehouse', props.model.warehouse_id) || '-'
})

const manufacturerCode = computed(() => display(props.model.manufacturer_code))

const manufacturerName = computed(() => {
  const name = props.model.manufacturer_name
  if (name != null && String(name).trim()) return String(name).trim()
  return resolveRefLabel('manufacturer', props.model.manufacturer_id) || '-'
})

const supplierCode = computed(() => display(props.model.supplier_code))

const supplierName = computed(() => {
  const name = props.model.supplier_name
  if (name != null && String(name).trim()) return String(name).trim()
  return resolveRefLabel('supplier', props.model.supplier_id) || '-'
})

const basicItems = computed(() => [
  { label: '品牌', value: display(props.model.brand) },
  { label: '规格', value: display(props.model.specification) },
  { label: '型号', value: display(props.model.model) },
  { label: '序列号', value: display(props.model.serial_number), mono: true },
  { label: '医疗器械注册证号', value: display(props.model.registration_no), mono: true },
  { label: '生产日期', value: display(props.model.production_date) },
  { label: '设备状态', value: statusLabel.value },
  { label: '启用日期', value: display(props.model.enable_date) },
  { label: '验收日期', value: display(props.model.acceptance_date) }
])

const locationItems = computed(() => [
  { label: '领用科室', value: deptLabel.value },
  { label: '仓库', value: warehouseLabel.value },
  { label: '存放位置', value: display(props.model.location_detail) }
])

const vendorItems = computed(() => [
  { label: '厂家编码', value: manufacturerCode.value },
  { label: '厂家名称', value: manufacturerName.value },
  { label: '供应商编码', value: supplierCode.value },
  { label: '供应商名称', value: supplierName.value }
])

const financeItems = computed(() => [
  { label: '原值', value: formatNumber(props.model.original_value) },
  { label: '净值', value: formatNumber(props.model.net_value) },
  { label: '购置日期', value: display(props.model.purchase_date) }
])

function display(val: unknown) {
  if (val === null || val === undefined || val === '') return '-'
  return String(val)
}

function formatNumber(val: unknown) {
  if (val === null || val === undefined || val === '') return '-'
  const num = Number(val)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : String(val)
}

async function renderQr() {
  if (!deviceCode.value) {
    qrDataUrl.value = ''
    return
  }
  qrDataUrl.value = await QRCode.toDataURL(deviceCode.value, { width: 160, margin: 1 })
}

async function loadPrints() {
  if (!deviceId.value) {
    prints.value = []
    return
  }
  historyLoading.value = true
  try {
    const { data } = await http.get(`/asset/device/${deviceId.value}/label`)
    prints.value = (data.data?.prints ?? []) as Record<string, unknown>[]
  } catch {
    prints.value = []
  } finally {
    historyLoading.value = false
  }
}

async function doPrint() {
  if (!deviceId.value || !deviceCode.value) {
    ElMessage.warning('设备编码为空，无法打印资产卡片')
    return
  }
  printing.value = true
  try {
    await http.post(`/asset/device/${deviceId.value}/label/print`, { template_code: 'asset_card' })
    ElMessage.success('已记录卡片打印')
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
  const win = window.open('', '_blank', 'width=900,height=1200')
  if (!win) {
    ElMessage.warning('请允许弹出窗口以打印资产卡片')
    return
  }
  win.document.write(`<!doctype html><html><head><title>医疗设备资产卡片</title>
    <style>
      @page { size: A4; margin: 16mm; }
      * { box-sizing: border-box; }
      body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; color: #222; margin: 0; }
      .device-asset-card { border: 1px solid #333; padding: 16px 18px; }
      .device-asset-card__header { text-align: center; border-bottom: 2px solid #222; padding-bottom: 10px; margin-bottom: 14px; }
      .device-asset-card__org { font-size: 14px; margin-bottom: 4px; }
      .device-asset-card__title { font-size: 20px; font-weight: 700; letter-spacing: 0.08em; }
      .device-asset-card__identity { display: flex; gap: 18px; align-items: flex-start; margin-bottom: 12px; }
      .device-asset-card__qr { width: 120px; text-align: center; flex-shrink: 0; }
      .device-asset-card__qr img { width: 110px; height: 110px; }
      .device-asset-card__qr-hint { display: block; font-size: 11px; color: #666; margin-top: 4px; }
      .device-asset-card__id-meta { flex: 1; display: flex; flex-direction: column; gap: 10px; padding-top: 8px; }
      .device-asset-card__section { margin-top: 12px; }
      .device-asset-card__section h4 { margin: 0 0 8px; font-size: 13px; border-left: 3px solid #222; padding-left: 8px; }
      .device-asset-card__grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px 14px; }
      .device-asset-card__field { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
      .device-asset-card__label { font-size: 11px; color: #666; }
      .device-asset-card__value { font-size: 13px; border-bottom: 1px solid #ccc; min-height: 22px; padding-bottom: 2px; word-break: break-all; }
      .device-asset-card__value.mono { font-family: ui-monospace, Consolas, monospace; }
      .device-asset-card__sign { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-top: 28px; padding-top: 12px; border-top: 1px dashed #999; }
      .device-asset-card__sign-item { font-size: 12px; }
      .device-asset-card__sign-line { display: block; margin-top: 28px; border-bottom: 1px solid #333; height: 1px; }
    </style></head><body>${node.outerHTML}</body></html>`)
  win.document.close()
  win.focus()
  win.print()
}

watch(
  () => [deviceCode.value, deviceId.value] as const,
  async () => {
    await renderQr()
    await loadPrints()
  },
  { immediate: true }
)

onMounted(async () => {
  await Promise.all([
    ensureRefLabelMap('department'),
    ensureRefLabelMap('campus'),
    ensureRefLabelMap('warehouse'),
    ensureRefLabelMap('manufacturer'),
    ensureRefLabelMap('supplier'),
    loadDict('device_status')
  ])
})
</script>

<style scoped>
.device-asset-card-wrap__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.device-asset-card {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  padding: 18px 20px;
}

.device-asset-card__header {
  text-align: center;
  border-bottom: 2px solid #303133;
  padding-bottom: 10px;
  margin-bottom: 14px;
}

.device-asset-card__org {
  font-size: 14px;
  color: #606266;
  margin-bottom: 4px;
}

.device-asset-card__title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 0.06em;
}

.device-asset-card__identity {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.device-asset-card__qr {
  width: 128px;
  flex-shrink: 0;
  text-align: center;
}

.device-asset-card__qr img {
  width: 120px;
  height: 120px;
  border: 1px solid #ebeef5;
}

.device-asset-card__qr-empty {
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #dcdfe6;
  color: #909399;
  font-size: 13px;
}

.device-asset-card__qr-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.device-asset-card__id-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
}

.device-asset-card__section {
  margin-top: 14px;
}

.device-asset-card__section h4 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  border-left: 3px solid var(--el-color-primary, #409eff);
  padding-left: 8px;
}

.device-asset-card__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
}

.device-asset-card__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.device-asset-card__label {
  font-size: 12px;
  color: #909399;
}

.device-asset-card__value {
  min-height: 22px;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
  color: #303133;
  word-break: break-all;
}

.device-asset-card__value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.device-asset-card__sign {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 28px;
  padding-top: 14px;
  border-top: 1px dashed #dcdfe6;
  font-size: 13px;
  color: #606266;
}

.device-asset-card__sign-line {
  display: block;
  margin-top: 28px;
  border-bottom: 1px solid #909399;
  height: 1px;
}

.device-asset-card-wrap__history {
  margin-top: 16px;
}

.device-asset-card-wrap__history h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .device-asset-card__identity {
    flex-direction: column;
  }

  .device-asset-card__grid,
  .device-asset-card__sign {
    grid-template-columns: 1fr;
  }
}
</style>
