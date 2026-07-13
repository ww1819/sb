<template>
  <div class="adverse-query-page">
    <el-row :gutter="12" class="summary-row">
      <el-col :span="6"><el-statistic title="事件总数" :value="summary.total ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="未结案" :value="summary.open_count ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="待受理" :value="summary.reported_count ?? 0" /></el-col>
      <el-col :span="6"><el-statistic title="已报监管" :value="summary.authority_count ?? 0" /></el-col>
    </el-row>

    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail" />

    <AppModal v-model="visible" title="不良事件详情" size="xl">
      <template v-if="event">
        <GroupedFormFields :table="config.table" :model="event" :fields="readonlyFields" />
      </template>
      <template #footer><el-button @click="visible = false">关闭</el-button></template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const config = getPageConfig('/qc/adverse/query')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const event = ref<Record<string, unknown> | null>(null)
const summary = ref<Record<string, number>>({})
const readonlyFields = getSchema('adverse_event').map((f) => ({ ...f, readonly: true }))

async function loadSummary() {
  const { data } = await http.get('/qc/adverse/summary')
  summary.value = data.data ?? {}
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/qc/adverse/${row.id}`)
  event.value = data.data
  visible.value = true
}

onMounted(loadSummary)
</script>

<style scoped>
.summary-row {
  margin-bottom: 12px;
}
</style>
