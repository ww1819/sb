<template>
  <span>{{ displayText }}</span>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { ensureRefLabelMap, labelCacheVersion, resolveRefLabel } from '@/composables/useRefLabelMap'

const props = withDefaults(
  defineProps<{
    linkTable: string
    value: unknown
    hideCode?: boolean
  }>(),
  { hideCode: false }
)

const displayText = computed(() => {
  labelCacheVersion.value
  if (props.value === null || props.value === undefined || props.value === '') return '-'
  const label = resolveRefLabel(props.linkTable, props.value, { hideCode: props.hideCode })
  return label || '-'
})

async function load() {
  await ensureRefLabelMap(props.linkTable, props.hideCode)
}

onMounted(load)
watch(() => props.linkTable, load)
watch(
  () => props.value,
  (v) => {
    if (v != null && v !== '') void load()
  }
)
</script>
