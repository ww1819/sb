<template>
  <div class="quick-entry-grid">
    <button
      v-for="item in items"
      :key="item.path"
      type="button"
      class="quick-entry-item"
      :style="{
        '--entry-color': item.color,
        '--entry-bg': item.bgColor
      }"
      @click="$emit('navigate', item.path)"
    >
      <div class="quick-entry-icon">
        <el-icon :size="24"><component :is="item.icon" /></el-icon>
      </div>
      <div class="quick-entry-text">
        <div class="quick-entry-label">{{ item.label }}</div>
        <div v-if="item.desc" class="quick-entry-desc">{{ item.desc }}</div>
      </div>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

export interface QuickEntryItem {
  label: string
  path: string
  icon: Component
  color: string
  bgColor: string
  desc?: string
}

defineProps<{
  items: QuickEntryItem[]
}>()

defineEmits<{
  navigate: [path: string]
}>()
</script>

<style scoped>
.quick-entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--meis-space-md);
}

.quick-entry-item {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-radius-lg);
  background: #fff;
  box-shadow: var(--meis-shadow-sm);
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.quick-entry-item:hover {
  transform: translateY(-2px);
  border-color: var(--entry-color);
  box-shadow: var(--meis-shadow-md);
}

.quick-entry-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: var(--entry-color);
  background: var(--entry-bg);
}

.quick-entry-label {
  font-size: var(--meis-font-subtitle);
  font-weight: 600;
  color: var(--meis-text-primary);
  line-height: 1.4;
}

.quick-entry-desc {
  margin-top: 4px;
  font-size: var(--meis-font-caption);
  color: var(--meis-text-secondary);
  line-height: 1.4;
}
</style>
