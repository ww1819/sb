<template>
  <div class="device-asset-card">
    <div class="device-asset-card__header">
      <span class="device-asset-card__brand">MEIS</span>
      <span class="device-asset-card__title">医疗设备资产卡片</span>
    </div>

    <div class="device-asset-card__body">
      <div class="device-asset-card__qr">
        <div class="device-asset-card__qr-box">
          <span class="device-asset-card__qr-text">{{ qrHint }}</span>
        </div>
        <span class="device-asset-card__qr-label">资产标签</span>
      </div>

      <div class="device-asset-card__columns">
        <div class="device-asset-card__column">
          <div v-for="item in leftItems" :key="item.label" class="device-asset-card__item">
            <span class="device-asset-card__label">{{ item.label }}</span>
            <span class="device-asset-card__value" :class="{ mono: item.mono }">{{ item.value }}</span>
          </div>
        </div>
        <div class="device-asset-card__column">
          <div v-for="item in rightItems" :key="item.label" class="device-asset-card__item">
            <span class="device-asset-card__label">{{ item.label }}</span>
            <span class="device-asset-card__value" :class="{ mono: item.mono }">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ensureRefLabelMap, resolveRefLabel } from '@/composables/useRefLabelMap'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  model: Record<string, unknown>
}>()

const { loadDict, getCached } = useDict()

onMounted(async () => {
  await Promise.all([ensureRefLabelMap('department'), loadDict('device_status')])
})

const brandModel = computed(() => {
  const brand = props.model.brand
  const modelName = props.model.model
  const parts = [brand, modelName].filter((v) => v !== null && v !== undefined && v !== '')
  return parts.length ? parts.join(' / ') : '-'
})

const financeText = computed(() => {
  const original = props.model.original_value
  const net = props.model.net_value
  if (original == null && net == null) return '-'
  return `${formatNumber(original)} / ${formatNumber(net)}`
})

const deptLabel = computed(() => {
  const label = resolveRefLabel('department', props.model.dept_id)
  return label || '-'
})

const statusLabel = computed(() => {
  const val = props.model.device_status
  if (val === null || val === undefined || val === '') return '-'
  const items = getCached('device_status')
  return items.find((i) => i.value === String(val))?.label ?? String(val)
})

const qrHint = computed(() => {
  const code = props.model.device_code
  return code ? String(code).slice(-4) : 'QR'
})

const leftItems = computed(() => [
  { label: '设备名称', value: display(props.model.device_name) },
  { label: '品牌型号', value: brandModel.value },
  { label: '设备状态', value: statusLabel.value },
  { label: '启用日期', value: display(props.model.enable_date) }
])

const rightItems = computed(() => [
  { label: '设备编码', value: display(props.model.device_code), mono: true },
  { label: '领用科室', value: deptLabel.value },
  { label: '折旧起点 / 当前净值', value: financeText.value }
])

function display(val: unknown) {
  if (val === null || val === undefined || val === '') return '-'
  return String(val)
}

function formatNumber(val: unknown) {
  if (val === null || val === undefined || val === '') return '-'
  const num = Number(val)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : String(val)
}
</script>

<style scoped>
.device-asset-card {
  padding: 20px 24px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.device-asset-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px dashed #dcdfe6;
}

.device-asset-card__brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  background: #52c41a;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.device-asset-card__title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.device-asset-card__body {
  display: flex;
  align-items: flex-start;
  gap: 28px;
}

.device-asset-card__qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  width: 96px;
}

.device-asset-card__qr-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background:
    linear-gradient(90deg, #ececec 1px, transparent 1px) 0 0 / 10px 10px,
    linear-gradient(#ececec 1px, transparent 1px) 0 0 / 10px 10px,
    #fafafa;
}

.device-asset-card__qr-text {
  font-size: 20px;
  font-weight: 700;
  color: #909399;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.device-asset-card__qr-label {
  font-size: 12px;
  color: #909399;
}

.device-asset-card__columns {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 40px;
}

.device-asset-card__column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.device-asset-card__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.device-asset-card__label {
  font-size: 13px;
  color: #909399;
  line-height: 1.4;
}

.device-asset-card__value {
  min-height: 22px;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
  color: #303133;
  line-height: 1.5;
  word-break: break-all;
}

.device-asset-card__value.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

@media (max-width: 900px) {
  .device-asset-card__body {
    flex-direction: column;
  }

  .device-asset-card__columns {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
