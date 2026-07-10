<template>
  <div class="device-record-panel">
    <div v-if="showFilter" class="device-record-panel__toolbar">
      <el-input
        v-model="keyword"
        :placeholder="filterPlaceholder"
        clearable
        class="device-record-panel__input"
      />
      <el-button type="primary">查询</el-button>
      <el-button>重置</el-button>
    </div>

    <el-table :data="rows" border stripe class="system-table device-record-panel__table">
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :min-width="col.minWidth ?? 120"
        show-overflow-tooltip
      />
      <template #empty>
        <PageEmpty :description="emptyText" :image-size="72" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageEmpty from '@/components/table/PageEmpty.vue'

export interface RecordColumn {
  prop: string
  label: string
  minWidth?: number
}

withDefaults(
  defineProps<{
    columns: RecordColumn[]
    emptyText?: string
    showFilter?: boolean
    filterPlaceholder?: string
  }>(),
  {
    emptyText: '暂无数据',
    showFilter: true,
    filterPlaceholder: '关键词搜索'
  }
)

const keyword = ref('')
const rows: Record<string, unknown>[] = []
</script>

<style scoped>
.device-record-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 280px;
}

.device-record-panel__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-record-panel__input {
  width: 240px;
}

.device-record-panel__table {
  width: 100%;
}

.device-record-panel__table :deep(.el-table__empty-block) {
  min-height: 220px;
}
</style>
