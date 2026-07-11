<template>
  <div class="shared-loan-page">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail" @add="openCreate">
      <template #toolbar-extra>
        <el-button type="primary" @click="openCreate">新增借调</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button v-if="['draft','pending'].includes(String(row.status))" link type="primary" @click="openDetail(row)">编辑</el-button>
        <el-button v-if="row.status === 'draft'" link type="warning" @click="submit(row)">提交</el-button>
        <el-button v-if="row.status === 'approved'" link type="success" @click="lend(row)">借出</el-button>
      </template>
    </CrudPage>
    <AppModal v-model="visible" :title="modalTitle" size="lg">
      <GroupedFormFields v-if="loan" :table="config.table" :model="loan" :fields="editableFields" />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="editable" type="primary" @click="save">保存</el-button>
        <el-button v-if="loan?.status === 'draft'" type="warning" @click="submit(loan!)">提交审批</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const auth = useAuthStore()
const config = getPageConfig('/shared/loan')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const loan = ref<Record<string, unknown> | null>(null)
const editable = computed(() => loan.value && ['draft', 'pending'].includes(String(loan.value.status)))
const modalTitle = computed(() => (loan.value?.id ? `借调单 ${loan.value.loan_no ?? ''}` : '新增借调申请'))
const editableFields = computed(() => {
  const fields = getSchema('shared_device_loan')
  return editable.value ? fields : fields.map((f) => ({ ...f, readonly: true }))
})

function openCreate() {
  loan.value = { status: 'draft', applicant_id: auth.user?.id }
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/shared/loan/${row.id}`)
  loan.value = data.data
  visible.value = true
}

async function save() {
  if (!loan.value) return
  const { data } = await http.post('/shared/loan', loan.value)
  loan.value = data.data
  visible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}

async function submit(row: Record<string, unknown>) {
  await http.post(`/shared/loan/${row.id}/submit`, { applicantId: auth.user?.id })
  ElMessage.success('已提交审批')
  visible.value = false
  crudRef.value?.load()
}

async function lend(row: Record<string, unknown>) {
  await http.post(`/shared/loan/${row.id}/lend`)
  ElMessage.success('已借出')
  crudRef.value?.load()
}
</script>
