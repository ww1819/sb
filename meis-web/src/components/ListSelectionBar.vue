<template>
  <div v-if="visible" class="list-selection-bar">
    <span class="list-selection-bar__count">已选 {{ count }} 条<span v-if="crossPageHint">（跨页保留）</span></span>
    <el-button link type="primary" :disabled="!hasCurrentPageRows" @click="$emit('select-page')">全选当页</el-button>
    <el-button link type="primary" :disabled="count === 0" @click="$emit('clear')">取消全选</el-button>
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    count: number
    visible?: boolean
    hasCurrentPageRows?: boolean
    crossPageHint?: boolean
  }>(),
  {
    visible: true,
    hasCurrentPageRows: true,
    crossPageHint: true
  }
)

defineEmits<{
  (e: 'select-page'): void
  (e: 'clear'): void
}>()
</script>

<style scoped>
.list-selection-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0 0 8px;
  min-height: 28px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.list-selection-bar__count {
  margin-right: 4px;
}
</style>
