<template>
  <AppModal
    :model-value="modelValue"
    :title="title"
    :size="size"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <slot />
    <template #footer>
      <div class="form-drawer-footer">
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" @click="$emit('save')">保存</el-button>
      </div>
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

<style scoped>
.form-drawer-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  width: 100%;
}
</style>
