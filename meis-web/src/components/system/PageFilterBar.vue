<template>
  <div class="page-filter-bar">
    <div class="filter-fields">
      <el-input
        :model-value="keyword"
        placeholder="用户名 / 姓名 / 工号 / 手机"
        clearable
        class="filter-keyword"
        :prefix-icon="Search"
        @update:model-value="$emit('update:keyword', $event)"
        @clear="$emit('search')"
        @keyup.enter="$emit('search')"
      />
      <slot name="filters" />
      <el-button type="primary" :icon="Search" @click="$emit('search')">查询</el-button>
      <el-button :icon="RefreshLeft" @click="$emit('reset')">重置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RefreshLeft, Search } from '@element-plus/icons-vue'

defineProps<{
  keyword?: string
}>()

defineEmits<{
  search: []
  reset: []
  'update:keyword': [value: string]
}>()
</script>

<style scoped>
.page-filter-bar {
  padding: var(--meis-space-md);
  background: var(--meis-surface-muted);
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  margin-bottom: var(--meis-space-md);
}

.filter-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.filter-keyword {
  width: 280px;
}
</style>
