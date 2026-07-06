<template>
  <div class="header-toolbar" :class="{ dark, compact: isMobile }">
    <button
      v-if="showMenu"
      type="button"
      class="toolbar-btn"
      :class="{ dark }"
      aria-label="打开菜单"
      @click="emit('open-menu')"
    >
      <el-icon :size="18"><Menu /></el-icon>
    </button>

    <MenuSearchTrigger :dark="dark" :compact="isMobile" @open="emit('open-search')" />
    <HeaderNotificationBell v-if="showNotifications" :dark="dark" @view-all="emit('view-all-messages')" />

    <button
      type="button"
      class="toolbar-btn"
      :class="{ dark }"
      :aria-label="fullscreen ? '退出全屏' : '内容全屏'"
      @click="emit('toggle-fullscreen')"
    >
      <el-icon :size="18">
        <component :is="fullscreen ? Close : FullScreen" />
      </el-icon>
    </button>

    <button
      type="button"
      class="toolbar-btn"
      :class="{ dark }"
      aria-label="帮助"
      @click="emit('open-help')"
    >
      <el-icon :size="18"><QuestionFilled /></el-icon>
    </button>

    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Close, FullScreen, Menu, QuestionFilled } from '@element-plus/icons-vue'
import { useLayoutStore } from '@/stores/layout'
import MenuSearchTrigger from './MenuSearchTrigger.vue'
import HeaderNotificationBell from './HeaderNotificationBell.vue'

defineProps<{
  dark?: boolean
  showNotifications?: boolean
  showMenu?: boolean
  fullscreen?: boolean
}>()

const emit = defineEmits<{
  'open-search': []
  'view-all-messages': []
  'open-menu': []
  'toggle-fullscreen': []
  'open-help': []
}>()

const layoutStore = useLayoutStore()
const isMobile = computed(() => layoutStore.isMobile)
</script>

<style scoped>
.header-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.header-toolbar.compact {
  gap: 6px;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--meis-text-primary);
  transition: background 0.15s ease;
}

.toolbar-btn.dark {
  color: rgba(255, 255, 255, 0.9);
}

.toolbar-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.toolbar-btn.dark:hover {
  background: rgba(255, 255, 255, 0.12);
}
</style>
