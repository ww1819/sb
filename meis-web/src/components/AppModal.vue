<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="dialogWidth"
    align-center
    destroy-on-close
    :close-on-click-modal="closeOnClickModal"
    :append-to="`#${LAYOUT_CONTENT_ROOT_ID}`"
    class="app-modal"
    :class="`app-modal--${size}`"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="app-modal__body">
      <slot />
    </div>
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { LAYOUT_CONTENT_ROOT_ID } from '@/config/app'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
    closeOnClickModal?: boolean
  }>(),
  { size: 'md', closeOnClickModal: false }
)

defineEmits<{ 'update:modelValue': [value: boolean] }>()

const dialogWidth = computed(() => {
  switch (props.size) {
    case 'sm':
      return '480px'
    case 'md':
      return '640px'
    case 'lg':
      return 'min(960px, 88vw)'
    case 'xl':
      return 'min(1200px, 92vw)'
    default:
      return '640px'
  }
})
</script>
