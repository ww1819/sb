<template>
  <div class="master-detail">
    <FormSection v-if="$slots.master" title="主表信息">
      <slot name="master" />
    </FormSection>
    <FormSection :title="detailTitle">
      <slot name="detail" />
      <el-table :data="items" border class="detail-table">
        <el-table-column v-if="showRowIndex" label="序号" width="60" align="center" fixed="left">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <slot name="detail-columns" />
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" @click="items.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="add-btn" @click="$emit('add-item')">添加明细</el-button>
    </FormSection>
  </div>
</template>

<script setup lang="ts">
import FormSection from '@/components/form/FormSection.vue'

withDefaults(
  defineProps<{
    items: Record<string, unknown>[]
    detailTitle?: string
    showRowIndex?: boolean
  }>(),
  { detailTitle: '明细信息', showRowIndex: true }
)
defineEmits<{ 'add-item': [] }>()
</script>

<style scoped>
.master-detail {
  display: flex;
  flex-direction: column;
}
.detail-table {
  width: 100%;
}
.master-detail :deep(.detail-table .el-table__header th) {
  background: var(--meis-modal-header-bg, var(--meis-surface-header, #f0f2f5)) !important;
}
.master-detail :deep(.detail-table .el-table__header th .cell) {
  white-space: nowrap;
  line-height: 1.4;
  padding: 0 10px;
  color: var(--meis-text-primary, #303133);
  font-weight: 600;
}
.master-detail :deep(.detail-table .el-table__body-wrapper) {
  max-height: 360px;
}
.add-btn {
  margin-top: 8px;
}
</style>
