<template>
  <div class="shared-record-page">
    <el-row :gutter="12" class="summary-row">
      <el-col :span="4"><el-statistic title="公用设备" :value="summary.device_count ?? 0" /></el-col>
      <el-col :span="4"><el-statistic title="借调总数" :value="summary.loan_total ?? 0" /></el-col>
      <el-col :span="4"><el-statistic title="借出中" :value="summary.on_loan_count ?? 0" /></el-col>
      <el-col :span="4"><el-statistic title="待审批借调" :value="summary.pending_loan ?? 0" /></el-col>
      <el-col :span="4"><el-statistic title="待审批归还" :value="summary.pending_return ?? 0" /></el-col>
      <el-col :span="4"><el-statistic title="已收费用" :value="Number(summary.fee_total ?? 0)" :precision="2" /></el-col>
    </el-row>
    <CrudPage :config="config" detail-mode hide-add @detail="openDetail" />
    <AppModal v-model="visible" title="借调记录详情" size="lg">
      <GroupedFormFields v-if="loan" table="shared_device_loan" :model="loan" :fields="readonlyFields" />
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

const config = getPageConfig('/shared/record')!
const summary = ref<Record<string, unknown>>({})
const visible = ref(false)
const loan = ref<Record<string, unknown> | null>(null)
const readonlyFields = getSchema('shared_device_loan').map((f) => ({ ...f, readonly: true }))

async function loadSummary() {
  const { data } = await http.get('/shared/record/summary')
  summary.value = data.data ?? {}
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/shared/loan/${row.id}`)
  loan.value = data.data
  visible.value = true
}

onMounted(loadSummary)
</script>

<style scoped>
.summary-row { margin-bottom: 12px; }
</style>
