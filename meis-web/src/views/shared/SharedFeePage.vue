<template>
  <div class="shared-fee-page">
    <CrudPage ref="crudRef" :config="config" detail-mode @detail="openDetail" @add="openCreate">
      <template #row-actions="{ row }">
        <el-button v-if="row.paid_status === 'unpaid'" link type="primary" @click="pay(row)">确认收费</el-button>
        <el-button link @click="openDetail(row)">编辑</el-button>
      </template>
    </CrudPage>
    <AppModal v-model="visible" :title="modalTitle" size="md">
      <GroupedFormFields v-if="fee" :table="config.table" :model="fee" :fields="formFields" />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const config = getPageConfig('/shared/fee')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const fee = ref<Record<string, unknown> | null>(null)
const modalTitle = computed(() => (fee.value?.id ? '编辑收费单' : '登记收费'))
const formFields = getSchema('shared_device_fee')

function openCreate() {
  fee.value = { paid_status: 'unpaid', fee_date: new Date().toISOString().slice(0, 10) }
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  fee.value = { ...row }
  visible.value = true
}

async function save() {
  if (!fee.value) return
  await http.post('/shared/fee', fee.value)
  ElMessage.success('保存成功')
  visible.value = false
  crudRef.value?.load()
}

async function pay(row: Record<string, unknown>) {
  await http.post(`/shared/fee/${row.id}/pay`)
  ElMessage.success('已确认收费')
  crudRef.value?.load()
}
</script>
