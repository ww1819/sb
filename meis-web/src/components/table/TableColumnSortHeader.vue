<template>
  <div class="table-sort-header">
    <span class="table-sort-header__label">{{ label }}</span>
    <span class="table-sort-header__btns">
      <button
        type="button"
        class="table-sort-header__btn"
        :class="{ active: sortField === field && sortOrder === 'asc' }"
        title="升序"
        @click.stop="emit('sort', field, 'asc')"
      >
        <el-icon><CaretTop /></el-icon>
      </button>
      <button
        type="button"
        class="table-sort-header__btn"
        :class="{ active: sortField === field && sortOrder === 'desc' }"
        title="降序"
        @click.stop="emit('sort', field, 'desc')"
      >
        <el-icon><CaretBottom /></el-icon>
      </button>
    </span>
  </div>
</template>

<script setup lang="ts">
import { CaretBottom, CaretTop } from '@element-plus/icons-vue'

defineProps<{
  label: string
  field: string
  sortField?: string | null
  sortOrder?: 'asc' | 'desc' | null
}>()

const emit = defineEmits<{ sort: [field: string, order: 'asc' | 'desc'] }>()
</script>
