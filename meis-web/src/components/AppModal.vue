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
    :modal-class="modalClass"
    class="app-modal"
    :class="[
      `app-modal--${size}`,
      placement === 'right' ? 'app-modal--right' : '',
      variant === 'report' ? 'app-modal--report' : '',
    ]"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header="{ close, titleId, titleClass }">
      <div class="app-modal__header" :class="{ 'app-modal__header--report': variant === 'report' }">
        <span :id="titleId" :class="[titleClass, variant === 'report' ? 'section-bar-title' : '']">{{ title }}</span>
        <div class="app-modal__header-actions">
          <slot name="header-actions" />
          <el-button plain @click="close">关闭</el-button>
        </div>
      </div>
    </template>
    <div class="app-modal__body" :class="{ 'app-modal__body--report': variant === 'report' }">
      <slot />
    </div>
    <template v-if="$slots.footer" #footer>
      <div v-if="variant === 'report'" class="app-modal__footer--report">
        <slot name="footer" />
      </div>
      <template v-else>
        <slot name="footer" />
      </template>
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
    /** report：与列表报表表面同款色条标题/边框/底栏；全站默认 report */
    variant?: 'default' | 'report'
  }>(),
  { size: 'md', placement: 'center', closeOnClickModal: false, variant: 'report' }
)

defineEmits<{ 'update:modelValue': [value: boolean] }>()

const modalClass = computed(() => {
  // Space-separated string is applied on el-overlay via modalClass
  const parts = ['layout-content-modal']
  if (props.variant === 'report') parts.push('layout-content-modal--report')
  if (props.placement === 'right') parts.push('layout-content-modal--right')
  return parts.join(' ')
})

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
