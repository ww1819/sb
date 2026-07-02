<template>
  <div class="master-detail">
    <el-card header="主表信息" class="master">
      <slot name="master" />
    </el-card>
    <el-card header="明细" class="detail">
      <slot name="detail" />
      <el-table :data="items" border>
        <slot name="detail-columns" />
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="items.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="add-btn" @click="$emit('add-item')">添加明细</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineProps<{ items: Record<string, unknown>[] }>()
defineEmits<{ 'add-item': [] }>()
</script>

<style scoped>
.master-detail { display: flex; flex-direction: column; gap: 16px; }
.add-btn { margin-top: 8px; }
</style>
