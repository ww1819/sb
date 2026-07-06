<template>
  <div class="system-page">
    <el-card shadow="never" class="system-card">
      <template v-if="showHeader" #header>
        <div class="system-header">
          <div v-if="showTitle || subtitle" class="system-header-text">
            <div v-if="showTitle" class="system-title">{{ title }}</div>
            <div v-if="subtitle" class="system-subtitle" :class="{ 'no-title': !showTitle }">{{ subtitle }}</div>
          </div>
          <div v-if="$slots.actions" class="system-actions">
            <slot name="actions" />
          </div>
        </div>
      </template>

      <div class="system-card-body">
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

        <div ref="tableWrapRef" v-loading="loading" class="system-table-wrap">
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
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref, useSlots } from 'vue'
import { useTableHeight } from '@/composables/useTableHeight'

const props = withDefaults(
  defineProps<{
    title: string
    showTitle?: boolean
    subtitle?: string
    loading?: boolean
    showSearch?: boolean
    showPager?: boolean
    keyword?: string
    page?: number
    size?: number
    total?: number
  }>(),
  { showTitle: false }
)

defineEmits<{
  search: []
  reset: []
  'page-change': []
  'update:keyword': [value: string]
  'update:page': [value: number]
  'update:size': [value: number]
}>()

const slots = useSlots()
const tableWrapRef = ref<HTMLElement | null>(null)
const tableHeight = useTableHeight(tableWrapRef)

provide('systemTableHeight', tableHeight)

const showHeader = computed(
  () => props.showTitle || !!props.subtitle || !!slots.actions
)
</script>

<style scoped>
.system-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.system-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
  box-shadow: var(--meis-card-shadow);
}

.system-card :deep(.el-card__header) {
  padding: 10px 16px;
  border-bottom: 1px solid var(--meis-border-light);
  background: #fff;
  flex-shrink: 0;
}

.system-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.system-card-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 16px;
  overflow: hidden;
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

.system-subtitle.no-title {
  margin-top: 0;
  padding-left: 0;
  font-size: 13px;
}

.system-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--meis-space-sm);
  flex-shrink: 0;
  margin-left: auto;
}

.system-filter-slot {
  flex-shrink: 0;
  margin-bottom: 12px;
}

.legacy-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--meis-space-sm);
  padding: var(--meis-space-md);
  margin-bottom: 12px;
  background: var(--meis-surface-muted);
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  flex-shrink: 0;
}

.search-input {
  width: 240px;
}

.system-table-wrap {
  flex: 1;
  min-height: 0;
  border-radius: var(--meis-card-radius);
  overflow: hidden;
}

.system-pager {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--meis-border-light);
}

.system-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 160px;
}
</style>
