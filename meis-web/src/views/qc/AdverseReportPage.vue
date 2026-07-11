<template>
  <div class="adverse-report-page">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail" @add="openCreate">
      <template #toolbar-extra>
        <el-button type="primary" @click="openCreate">新增上报</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button v-if="row.status === 'reported'" link type="warning" @click="openDetail(row)">编辑</el-button>
        <el-button v-if="row.status === 'reported'" link type="primary" @click="transition(row, 'handling')">受理</el-button>
        <el-button v-if="row.status === 'handling'" link type="primary" @click="transition(row, 'reviewed')">审核</el-button>
        <el-button v-if="row.status === 'reviewed'" link type="success" @click="transition(row, 'closed')">结案</el-button>
        <el-button v-if="row.status !== 'closed' && !row.reported_to_authority" link type="danger" @click="reportRegulator(row)">报监管</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="event">
        <GroupedFormFields :table="config.table" :model="event" :fields="formFields" />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="editable" type="primary" @click="save">保存</el-button>
        <el-button v-if="event?.status === 'reported'" type="warning" @click="transition(event!, 'handling')">受理</el-button>
        <el-button v-if="event?.status === 'handling'" type="primary" @click="transition(event!, 'reviewed')">审核</el-button>
        <el-button v-if="event?.status === 'reviewed'" type="success" @click="transition(event!, 'closed')">结案</el-button>
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

const config = getPageConfig('/qc/adverse/report')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const event = ref<Record<string, unknown> | null>(null)

const modalTitle = computed(() => {
  if (!event.value?.id) return '不良事件 新增上报'
  return `不良事件 ${event.value.event_no ?? ''}`
})
const editable = computed(() => event.value != null && event.value.status === 'reported')
const formFields = computed(() => {
  const fields = getSchema('adverse_event')
  if (editable.value) return fields
  return fields.map((f) => ({ ...f, readonly: true }))
})

function openCreate() {
  event.value = { status: 'reported', reported_to_authority: false }
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/qc/adverse/${row.id}`)
  event.value = data.data
  visible.value = true
}

async function save() {
  if (!event.value) return
  const { data } = await http.post('/qc/adverse', event.value)
  event.value = data.data
  visible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}

async function transition(row: Record<string, unknown>, status: string) {
  const payload: Record<string, unknown> = { status }
  if (status === 'handling') payload.handle_measures = row.handle_measures
  if (status === 'reviewed') payload.review_comment = row.review_comment
  await http.post(`/qc/adverse/${row.id}/transition`, payload)
  ElMessage.success('操作成功')
  if (event.value?.id && String(event.value.id) === String(row.id)) {
    const { data } = await http.get(`/qc/adverse/${row.id}`)
    event.value = data.data
  }
  crudRef.value?.load()
}

async function reportRegulator(row: Record<string, unknown>) {
  await http.post(`/qc/adverse/${row.id}/report-regulator`, {
    authority_feedback: row.authority_feedback,
    report_date: row.report_date
  })
  ElMessage.success('已标记上报监管')
  crudRef.value?.load()
}
</script>
