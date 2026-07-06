<template>
  <AppModal
    :model-value="modelValue"
    :title="title"
    :size="size"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <slot />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="$emit('save')">保存</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import AppModal from './AppModal.vue'

withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
  }>(),
  { size: 'md' }
)

defineEmits<{ 'update:modelValue': [v: boolean]; save: [] }>()
</script>
