<template>
  <div class="shared-return-approve-page">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail">
      <template #row-actions="{ row }">
        <el-button link type="success" @click="approve(row)">通过</el-button>
        <el-button link type="danger" @click="reject(row)">驳回</el-button>
      </template>
    </CrudPage>
    <AppModal v-model="visible" title="归还审批详情" size="lg">
      <GroupedFormFields v-if="record" :table="config.table" :model="record" :fields="readonlyFields" />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="record?.status === 'pending'" type="success" @click="approve(record!)">通过</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const config = getPageConfig('/shared/return-approve')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const record = ref<Record<string, unknown> | null>(null)
const readonlyFields = getSchema('shared_device_return').map((f) => ({ ...f, readonly: true }))

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/shared/return/${row.id}`)
  record.value = data.data
  visible.value = true
}

async function approve(row: Record<string, unknown>) {
  await http.post(`/shared/return/${row.id}/approve`)
  ElMessage.success('归还已确认')
  visible.value = false
  crudRef.value?.load()
}

async function reject(row: Record<string, unknown>) {
  await http.post(`/shared/return/${row.id}/reject`)
  ElMessage.success('已驳回')
  crudRef.value?.load()
}
</script>
