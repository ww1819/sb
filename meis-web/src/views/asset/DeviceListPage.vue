<template>
  <div class="device-list-page">
    <CrudPage
      :config="config"
      enable-view
      delete-url="/asset/device"
      :operation-column-width="280"
      :can-delete="(row) => row.can_delete !== false"
    >
      <template #form="{ form, fields, mode }">
        <DeviceLedgerForm :model="form" :fields="fields" :mode="mode" />
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="onPrint(row)">打印</el-button>
      </template>
    </CrudPage>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import CrudPage from '@/components/CrudPage.vue'
import DeviceLedgerForm from '@/components/asset/DeviceLedgerForm.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printAssetLabelFromRow } from '@/utils/printAssetLabel'

const config = getPageConfig('/asset/device')!

async function onPrint(row: Record<string, unknown>) {
  try {
    await printAssetLabelFromRow(row)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '打印失败'
    ElMessage.error(msg)
  }
}
</script>

<style scoped>
.device-list-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
