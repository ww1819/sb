<template>
  <button
    type="button"
    class="search-trigger"
    :class="{ dark, compact }"
    @click="emit('open')"
  >
    <el-icon><Search /></el-icon>
    <span v-if="!compact" class="search-text">搜索菜单</span>
    <kbd v-if="!compact" class="search-kbd">Ctrl K</kbd>
  </button>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

defineProps<{
  dark?: boolean
  compact?: boolean
}>()

const emit = defineEmits<{
  open: []
}>()
</script>

<style scoped>
.search-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--meis-border-light);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.search-trigger:not(.dark) {
  background: var(--meis-surface-muted);
  color: var(--meis-text-secondary);
}

.search-trigger.dark:hover {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.25);
}

.search-trigger:not(.dark):hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.search-text {
  font-size: 13px;
}

.search-kbd {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.4;
  background: rgba(0, 0, 0, 0.18);
  color: inherit;
  font-family: inherit;
}

.search-trigger.compact {
  width: 34px;
  padding: 0;
  justify-content: center;
}

.search-trigger:not(.dark) .search-kbd {
  background: #fff;
  border: 1px solid var(--meis-border-light);
}
</style>
