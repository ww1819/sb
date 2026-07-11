<template>
  <WorkflowCrudPage :config="config" save-url="/asset/scrap" business-type="device_scrap">
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id" @click="evaluate(form, reload)">评估</el-button>
      <el-button v-if="form?.id" @click="dispose(form, reload)">处置归档</el-button>
    </template>
  </WorkflowCrudPage>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import http from '@/api/http'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const route = useRoute()
const path = computed(() => '/' + String(route.params.module) + '/' + String(route.params.page))
const config = computed(() => getPageConfig(path.value)!)

async function evaluate(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/asset/scrap/${form.id}/evaluate`, {
    evaluator_id: form.evaluator_id,
    evaluation_result: form.evaluation_result,
    residual_value: form.residual_value
  })
  reload?.()
}

async function dispose(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/asset/scrap/${form.id}/dispose`, {
    disposal_method: form.disposal_method,
    disposal_date: form.disposal_date
  })
  reload?.()
}
</script>
