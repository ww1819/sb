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
  isAmountField,
  isBooleanField,
  isNumericField,
  isStatusField
} from '@/utils/tableCell'
import { formatDisplayDate, formatDisplayDateTime } from '@/utils/datetime'
import { resolveRefLabel, labelCacheVersion } from '@/composables/useRefLabelMap'
import { useDict } from '@/composables/useDict'
import { openFilePreview } from '@/composables/useFilePreview'
import { resolveCodedLabel } from '@/i18n/resolveCodedLabel'

const props = defineProps<{
  /** 列表/详情单元格只需 prop（及可选 dictType 等），label 可省略 */
  field: Pick<FieldSchema, 'prop'> & Partial<Omit<FieldSchema, 'prop'>>
  value: unknown
  /** 行上联表/快照名称，外键缓存未命中时优先展示 */
  labelHint?: string
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
    const hint = (props.labelHint ?? '').trim()
    if (hint) return hint
    if (label) return label
  }
  if (props.field.type === 'date' || props.field.type === 'datetime') {
    if (props.field.type === 'date') return formatDisplayDate(props.value)
    return formatDisplayDateTime(props.value)
  }
  // 分类/枚举字典：未命中才「未知(码)」；编码/名称/规格等属性字段原样展示
  if (props.field.dictType) {
    return resolveCodedLabel({
      value: props.value,
      prop: props.field.prop,
      dictType: props.field.dictType
    })
  }
  return String(props.value)
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
