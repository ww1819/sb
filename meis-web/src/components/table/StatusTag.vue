<template>
  <el-tag :type="tagType" size="small" effect="light" round>
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { formatStatusLabel, statusTagType } from '@/utils/tableCell'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  value: unknown
  prop?: string
  dictType?: string
}>()

const { loadDict, resolveDictLabel } = useDict()

onMounted(() => {
  if (props.dictType) void loadDict(props.dictType)
})
watch(
  () => props.dictType,
  (t) => {
    if (t) void loadDict(t)
  }
)

const tagType = computed(() => statusTagType(props.value))
const label = computed(() => {
  const fromDict = resolveDictLabel(props.dictType, props.value)
  if (fromDict) return fromDict
  // 合同简化审批：字典未命中时按码值映射，避免列表显示 draft
  if (props.dictType === 'contract_approval_status') {
    const s = String(props.value ?? '')
    if (s === 'approved') return '已审批'
    if (s) return '未审批'
  }
  if (props.dictType === 'acceptance_review_status') {
    const s = String(props.value ?? '')
    if (s === 'approved') return '已审核'
    if (s) return '未审核'
  }
  return formatStatusLabel(props.value, props.prop)
})
</script>
