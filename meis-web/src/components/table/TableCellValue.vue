<template>
  <StatusTag v-if="showStatus" :value="value" :prop="field.prop" :dict-type="field.dictType" />
  <span v-else-if="showAmount" class="cell-number cell-amount">{{ formattedNumber }}</span>
  <span v-else-if="showNumeric" class="cell-number">{{ formattedNumber }}</span>
  <span v-else>{{ displayText }}</span>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
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

const props = defineProps<{
  field: FieldSchema
  value: unknown
}>()

const { loadDict, resolveDictLabel } = useDict()

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

const formattedNumber = computed(() => formatCellNumber(props.value, showAmount.value))
const displayText = computed(() => {
  // 订阅外键标签缓存版本，预加载完成后触发重绘（避免一直显示 UUID）
  void labelCacheVersion.value
  if (props.value === null || props.value === undefined || props.value === '') return '-'
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
