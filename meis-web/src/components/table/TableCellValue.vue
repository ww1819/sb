<template>
  <StatusTag v-if="showStatus" :value="value" :prop="field.prop" :dict-type="field.dictType" />
  <span v-else-if="showAmount" class="cell-number cell-amount">{{ formattedNumber }}</span>
  <span v-else-if="showNumeric" class="cell-number">{{ formattedNumber }}</span>
  <el-button
    v-else-if="showFile"
    link
    type="primary"
    :loading="previewing"
    @click="onPreview"
  >
    预览
  </el-button>
  <span v-else>{{ displayText }}</span>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StatusTag from './StatusTag.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import {
  formatCellNumber,
  formatStatusLabel,
  isAmountField,
  isBooleanField,
  isNumericField,
  isStatusField
} from '@/utils/tableCell'
import { resolveRefLabel, labelCacheVersion } from '@/composables/useRefLabelMap'
import { useDict } from '@/composables/useDict'
import { openFilePreview } from '@/composables/useFilePreview'

const props = defineProps<{
  field: FieldSchema
  value: unknown
}>()

const { loadDict, resolveDictLabel } = useDict()
const previewing = ref(false)

onMounted(() => {
  if (props.field.dictType) void loadDict(props.field.dictType)
})
watch(
  () => props.field.dictType,
  (t) => {
    if (t) void loadDict(t)
  }
)

const showStatus = computed(
  () => isStatusField(props.field.prop, props.field.dictType) || isBooleanField(props.field.prop, props.value)
)
const showAmount = computed(() => isAmountField(props.field.prop, props.field.type))
const showNumeric = computed(() => isNumericField(props.field.prop, props.field.type))
const fileUrl = computed(() => {
  if (props.field.type !== 'file') return ''
  const v = props.value
  if (v === null || v === undefined || v === '') return ''
  const s = String(v)
  return s.startsWith('http') || s.startsWith('/api') ? s : `/api${s}`
})
const showFile = computed(() => props.field.type === 'file' && !!fileUrl.value)

async function onPreview() {
  if (!fileUrl.value || previewing.value) return
  previewing.value = true
  try {
    await openFilePreview(fileUrl.value, '附件预览')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '预览失败'
    ElMessage.error(msg || '预览失败')
  } finally {
    previewing.value = false
  }
}

const formattedNumber = computed(() => formatCellNumber(props.value, showAmount.value))
const displayText = computed(() => {
  // 订阅外键标签缓存版本，预加载完成后触发重绘（避免一直显示 UUID）
  void labelCacheVersion.value
  if (props.value === null || props.value === undefined || props.value === '') return '-'
  if (props.field.type === 'file') return '-'
  const fromDict = resolveDictLabel(props.field.dictType, props.value)
  if (fromDict) return fromDict
  if (props.field.linkTable) {
    const label = resolveRefLabel(props.field.linkTable, props.value)
    if (label && label !== String(props.value)) return label
    if (label) return label
  }
  return formatStatusLabel(props.value, props.field.prop)
})
</script>

<style scoped>
.cell-number {
  display: inline-block;
  width: 100%;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--meis-text-primary);
}

.cell-amount {
  font-weight: 600;
  color: var(--meis-status-info);
}
</style>
