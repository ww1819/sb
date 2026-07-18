<template>
  <AppModal
    :model-value="modelValue"
    :title="title"
    :size="size"
    :placement="placement"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header-actions>
      <slot name="header-actions" />
    </template>
    <slot />
    <template #footer>
      <div class="form-drawer-footer">
        <el-button @click="$emit('update:modelValue', false)">{{ showSave ? '取消' : '关闭' }}</el-button>
        <el-button v-if="showSave" type="primary" @click="$emit('save')">保存</el-button>
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
    placement?: 'center' | 'right'
    showSave?: boolean
  }>(),
  { size: 'md', placement: 'center', showSave: true }
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
