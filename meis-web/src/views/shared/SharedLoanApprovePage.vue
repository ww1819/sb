<template>
  <div class="shared-loan-approve-page">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail">
      <template #row-actions="{ row }">
        <el-button link type="success" @click="approve(row)">通过</el-button>
        <el-button link type="danger" @click="reject(row)">驳回</el-button>
        <el-button link type="primary" @click="openDetail(row)">详情</el-button>
      </template>
    </CrudPage>
    <AppModal v-model="visible" title="借调审批详情" size="lg">
      <GroupedFormFields v-if="loan" :table="config.table" :model="loan" :fields="readonlyFields" />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="loan?.status === 'pending'" type="success" @click="approve(loan!)">通过</el-button>
        <el-button v-if="loan?.status === 'pending'" type="danger" @click="reject(loan!)">驳回</el-button>
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

const config = getPageConfig('/shared/loan-approve')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const loan = ref<Record<string, unknown> | null>(null)
const readonlyFields = getSchema('shared_device_loan').map((f) => ({ ...f, readonly: true }))

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/shared/loan/${row.id}`)
  loan.value = data.data
  visible.value = true
}

async function approve(row: Record<string, unknown>) {
  await http.post(`/shared/loan/${row.id}/approve`)
  ElMessage.success('已通过')
  visible.value = false
  crudRef.value?.load()
}

async function reject(row: Record<string, unknown>) {
  await http.post(`/shared/loan/${row.id}/reject`)
  ElMessage.success('已驳回')
  visible.value = false
  crudRef.value?.load()
}
</script>
