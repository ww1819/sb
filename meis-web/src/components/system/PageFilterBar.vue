<template>
  <div class="page-filter-bar">
    <div class="filter-fields">
      <slot name="prepend" />
      <slot name="keyword">
        <el-input
          :model-value="keyword"
          :placeholder="placeholder"
          clearable
          class="filter-keyword"
          :prefix-icon="Search"
          @update:model-value="$emit('update:keyword', $event)"
          @clear="$emit('search')"
          @keyup.enter="$emit('search')"
        />
      </slot>
      <slot name="filters" />
      <el-button v-if="showSearchButtons" type="primary" :icon="Search" @click="$emit('search')">查询</el-button>
      <el-button v-if="showSearchButtons" :icon="RefreshLeft" @click="$emit('reset')">重置</el-button>
      <slot name="trailing" />
    </div>
    <div v-if="$slots.actions" class="filter-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { RefreshLeft, Search } from '@element-plus/icons-vue'

withDefaults(
  defineProps<{
    keyword?: string
    placeholder?: string
    /** 是否在筛选区显示查询/重置（可改由 actions 槽自行放置） */
    showSearchButtons?: boolean
  }>(),
  { placeholder: '用户名 / 姓名 / 工号 / 手机', showSearchButtons: true }
)

defineEmits<{
  search: []
  reset: []
  'update:keyword': [value: string]
}>()
</script>

<style scoped>
.page-filter-bar {
  padding: 12px var(--meis-space-md);
  background: var(--meis-surface-muted);
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  margin-bottom: 0;
}

.filter-fields,
.filter-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.filter-actions {
  margin-top: 8px;
}

.filter-keyword {
  width: 280px;
}
</style>
