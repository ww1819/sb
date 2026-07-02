<template>
  <el-drawer :model-value="modelValue" :title="title" size="480px" @close="$emit('update:modelValue', false)">
    <slot />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="$emit('save')">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
defineProps<{ modelValue: boolean; title: string }>()
defineEmits<{ 'update:modelValue': [v: boolean]; save: [] }>()
</script>
