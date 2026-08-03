<template>
  <AppModal
    :model-value="modelValue"
    :title="title"
    :size="size"
    :placement="placement"
    :variant="variant"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header-actions>
      <slot name="header-actions" />
    </template>
    <slot />
    <template #footer>
      <div class="form-drawer-footer">
        <slot name="footer-before" />
        <el-button @click="$emit('update:modelValue', false)">{{ showSave ? '取消' : '关闭' }}</el-button>
        <el-button v-if="showSave" type="primary" @click="$emit('save')">保存</el-button>
        <slot name="footer-after" />
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
    variant?: 'default' | 'report'
  }>(),
  { size: 'md', placement: 'center', showSave: true, variant: 'report' }
)

defineEmits<{ 'update:modelValue': [v: boolean]; save: [] }>()
</script>

<style scoped>
.form-drawer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  width: 100%;
}
</style>
