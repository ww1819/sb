<template>
  <div class="system-page" :class="{ 'system-page--report': isReport }">
    <el-card shadow="never" class="system-card" :class="{ 'system-card--report': isReport }">
      <template v-if="showHeader && !isReport" #header>
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

      <div class="system-card-body" :class="{ 'system-card-body--report': isReport }">
        <!-- report：查询条件色条（含 filterBar / 简易搜索） -->
        <div
          v-if="isReport && ($slots.filterBar || $slots.filter || showSearch)"
          class="meis-query-box"
        >
          <div class="meis-section-bar">查询条件</div>
          <div class="meis-query-body">
            <template v-if="$slots.filterBar">
              <slot name="filterBar" />
            </template>
            <template v-else>
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
            </template>
          </div>
        </div>

        <div v-else-if="$slots.filterBar" class="system-filter-slot">
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

        <div
          v-loading="loading"
          class="system-table-wrap"
          :class="{ 'meis-table-wrap': isReport }"
        >
          <div v-if="isReport" class="meis-section-bar">{{ tableTitle }}</div>
          <div ref="tableWrapRef" class="system-table-fill">
            <slot name="table">
              <slot />
            </slot>
            <div v-if="$slots.empty" class="system-empty">
              <slot name="empty" />
            </div>
          </div>
          <div v-if="isReport && showPager" class="meis-table-footer">
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

        <div v-if="!isReport && showPager" class="system-pager">
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

export type SystemPageVariant = 'default' | 'report'

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
    /** report = 价值结构表同款；全站默认 report */
    variant?: SystemPageVariant
  }>(),
  { showTitle: false, variant: 'report' }
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

const isReport = computed(() => props.variant === 'report')
const tableTitle = computed(() => props.title || '列表')

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

.system-page--report {
  padding-bottom: 16px;
  box-sizing: border-box;
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

.system-card--report {
  border-radius: 0;
  box-shadow: none;
  border-color: transparent;
  background: transparent;
}

.system-card--report :deep(.el-card__body) {
  background: transparent;
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
  padding: 8px 16px 0;
  overflow: hidden;
}

.system-card-body--report {
  padding: 0;
  gap: 0;
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
  margin-bottom: 8px;
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
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--meis-card-radius);
}

.system-page--report .system-table-wrap {
  border-radius: 2px;
}

.system-table-fill {
  flex: 1;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  /* 避免 overflow:hidden 干扰 el-table 固定列 sticky */
  overflow: clip;
}

.system-table-wrap :deep(.system-table) {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.system-pager {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  margin-top: 0;
  padding: 4px 0 2px;
  border-top: 1px solid var(--meis-border-light);
}

.system-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 160px;
}

.meis-query-body {
  display: block;
}

.meis-query-body :deep(.page-filter-bar) {
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 0;
}
</style>
