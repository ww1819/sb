<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="dialogWidth"
    :align-center="placement === 'center'"
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="closeOnClickModal"
    :append-to="appendTarget"
    :z-index="layoutModalZIndex"
    modal-class="layout-content-modal"
    class="app-modal"
    :class="[`app-modal--${size}`, placement === 'right' ? 'app-modal--right' : '']"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header="{ close, titleId, titleClass }">
      <div class="app-modal__header">
        <span :id="titleId" :class="titleClass">{{ title }}</span>
        <el-button plain @click="close">关闭</el-button>
      </div>
    </template>
    <div class="app-modal__body">
      <slot />
    </div>
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, shallowRef } from 'vue'
import { LAYOUT_CONTENT_ROOT_ID } from '@/config/app'

const appendTarget = shallowRef<string | HTMLElement>(`#${LAYOUT_CONTENT_ROOT_ID}`)
/** 低于顶栏下拉（z-index 5000），避免遮挡系统菜单 */
const layoutModalZIndex = 100

onMounted(() => {
  const root = document.getElementById(LAYOUT_CONTENT_ROOT_ID)
  if (root) appendTarget.value = root
})

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    size?: 'sm' | 'md' | 'lg' | 'xl' | 'xxl'
    /** center：内容区居中；right：贴内容区右侧（不遮挡顶栏/侧栏） */
    placement?: 'center' | 'right'
    closeOnClickModal?: boolean
  }>(),
  { size: 'md', placement: 'center', closeOnClickModal: false }
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
    case 'xxl':
      return 'min(1480px, 96vw)'
    default:
      return '640px'
  }
})
</script>
