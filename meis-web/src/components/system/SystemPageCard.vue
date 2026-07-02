<template>
  <div class="system-page">
    <el-card shadow="never" class="system-card">
      <template #header>
        <div class="system-header">
          <div class="system-header-text">
            <div class="system-title">{{ title }}</div>
            <div v-if="subtitle" class="system-subtitle">{{ subtitle }}</div>
          </div>
          <div class="system-actions">
            <slot name="actions" />
          </div>
        </div>
      </template>

      <div v-if="$slots.filterBar" class="system-filter-slot">
        <slot name="filterBar" />
      </div>

      <div v-else-if="$slots.filter || showSearch" class="system-filter-bar legacy-filter">
        <el-input
          v-if="showSearch"
          :model-value="keyword"
          placeholder="关键词搜索"
          clearable
          class="search-input"
          @update:model-value="$emit('update:keyword', $event)"
          @clear="$emit('search')"
          @keyup.enter="$emit('search')"
        />
        <el-button v-if="showSearch" type="primary" @click="$emit('search')">查询</el-button>
        <el-button v-if="showSearch" @click="$emit('reset')">重置</el-button>
        <slot name="filter" />
      </div>

      <div v-loading="loading" class="system-table-wrap">
        <slot name="table">
          <slot />
        </slot>
        <div v-if="$slots.empty" class="system-empty">
          <slot name="empty" />
        </div>
      </div>

      <div v-if="showPager" class="system-pager">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @update:current-page="$emit('update:page', $event)"
          @update:page-size="$emit('update:size', $event)"
          @current-change="$emit('page-change')"
          @size-change="$emit('page-change')"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
  loading?: boolean
  showSearch?: boolean
  showPager?: boolean
  keyword?: string
  page?: number
  size?: number
  total?: number
}>()

defineEmits<{
  search: []
  reset: []
  'page-change': []
  'update:keyword': [value: string]
  'update:page': [value: number]
  'update:size': [value: number]
}>()
</script>

<style scoped>
.system-page {
  height: 100%;
}

.system-card {
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
  box-shadow: var(--meis-card-shadow);
}

.system-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--meis-border-light);
  background: #fff;
}

.system-card :deep(.el-card__body) {
  padding: 20px;
}

.system-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--meis-space-md);
}

.system-header-text {
  min-width: 0;
}

.system-title {
  position: relative;
  padding-left: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--meis-text-primary);
  line-height: 1.4;
}

.system-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  background: var(--el-color-primary);
}

.system-subtitle {
  margin-top: 6px;
  padding-left: 12px;
  font-size: 12px;
  color: var(--meis-text-secondary);
  line-height: 1.5;
}

.system-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--meis-space-sm);
  flex-shrink: 0;
}

.system-filter-slot {
  margin-bottom: var(--meis-space-md);
}

.legacy-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--meis-space-sm);
  padding: var(--meis-space-md);
  margin-bottom: var(--meis-space-md);
  background: var(--meis-surface-muted);
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
}

.search-input {
  width: 240px;
}

.system-table-wrap {
  min-height: 200px;
  border-radius: var(--meis-card-radius);
  overflow: hidden;
}

.system-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--meis-space-md);
  padding-top: var(--meis-space-md);
  border-top: 1px solid var(--meis-border-light);
}
</style>
