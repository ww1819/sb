<template>
  <el-tag :type="tagType" size="small" effect="light" round>
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatStatusLabel, statusTagType } from '@/utils/tableCell'

const props = defineProps<{
  value: unknown
  prop?: string
}>()

const tagType = computed(() => statusTagType(props.value))
const label = computed(() => formatStatusLabel(props.value, props.prop))
</script>
