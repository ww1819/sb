<template>
  <el-table :data="rows" stripe class="system-table">
    <el-table-column
      v-for="col in columns"
      :key="col.prop"
      :prop="col.prop"
      :label="col.label"
      min-width="120"
      show-overflow-tooltip
    >
      <template v-if="col.dict" #default="{ row }">
        <TableCellValue :field="{ prop: col.prop, label: col.label, dictType: col.dict }" :value="row[col.prop]" />
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import TableCellValue from '@/components/table/TableCellValue.vue'

defineProps<{
  rows: Record<string, unknown>[]
  columns: { prop: string; label: string; dict?: string }[]
}>()
</script>
