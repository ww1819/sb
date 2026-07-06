<template>
  <div class="stat-card" :style="{ '--stat-accent': color, '--stat-accent-bg': bgColor }">
    <div class="stat-icon">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-body">
      <div class="stat-value">{{ displayValue }}</div>
      <div class="stat-title">{{ title }}</div>
      <div v-if="hint" class="stat-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

const props = defineProps<{
  title: string
  value: number | string
  icon: Component
  color?: string
  bgColor?: string
  hint?: string
}>()

const displayValue = computed(() => {
  const n = Number(props.value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : props.value
})

const color = computed(() => props.color ?? '#1677ff')
const bgColor = computed(() => props.bgColor ?? 'rgba(22, 119, 255, 0.08)')
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  box-shadow: var(--meis-card-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--meis-shadow-md);
}

.stat-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: var(--stat-accent);
  background: var(--stat-accent-bg);
}

.stat-body {
  min-width: 0;
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--meis-text-primary);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-title {
  margin-top: 6px;
  font-size: 14px;
  color: var(--meis-text-secondary);
}

.stat-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--meis-text-secondary);
}
</style>
