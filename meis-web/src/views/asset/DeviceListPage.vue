<template>
  <div class="device-list-page">
    <CrudPage
      ref="crudRef"
      :config="config"
      enable-view
      delete-url="/asset/device"
      :can-delete="(row) => row.can_delete !== false"
    >
      <template #form="{ form, fields, mode }">
        <DeviceLedgerForm :model="form" :fields="fields" :mode="mode" />
      </template>
      <template #actions-after>
        <el-button @click="onBatchPrint">打印</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="onPrint(row)">打印</el-button>
      </template>
    </CrudPage>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import CrudPage from '@/components/CrudPage.vue'
import DeviceLedgerForm from '@/components/asset/DeviceLedgerForm.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printAssetLabelFromRow, printAssetLabelsBatch } from '@/utils/printAssetLabel'

const config = getPageConfig('/asset/device')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)

async function onPrint(row: Record<string, unknown>) {
  try {
    await printAssetLabelFromRow(row)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '打印失败'
    ElMessage.error(msg)
  }
}

async function onBatchPrint() {
  const rows = crudRef.value?.getSelectedRows() ?? []
  if (!rows.length) {
    ElMessage.warning('请先勾选要打印的设备')
    return
  }
  try {
    await printAssetLabelsBatch(rows)
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
