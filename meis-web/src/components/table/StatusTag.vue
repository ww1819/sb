<template>
  <el-tag :type="tagType" size="small" effect="light" round>
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { formatStatusLabel, statusTagType } from '@/utils/tableCell'
import { useDict } from '@/composables/useDict'
import { resolveCodedLabel } from '@/i18n/resolveCodedLabel'

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
  if (props.prop?.startsWith('is_') || typeof props.value === 'boolean') {
    return formatStatusLabel(props.value, props.prop, props.dictType)
  }
  return resolveCodedLabel({
    value: props.value,
    prop: props.prop,
    dictType: props.dictType,
    fromDict: resolveDictLabel(props.dictType, props.value)
  })
})
</script>
