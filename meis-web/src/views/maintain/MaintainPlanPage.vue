<template>
  <WorkflowCrudPage :config="config" save-url="/maintain/plan">
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id" @click="activate(form, reload)">激活计划</el-button>
      <el-button @click="loadDue">到期提醒</el-button>
    </template>
    <template #drawer-extra>
      <el-alert v-if="dueList.length" :title="`近7天到期 ${dueList.length} 项`" type="warning" show-icon class="due-alert" />
    </template>
  </WorkflowCrudPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import http from '@/api/http'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '保养计划', apiBase: '/maintain', table: 'maintenance_plan' }
const dueList = ref<Record<string, unknown>[]>([])

async function activate(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/maintain/plan/${form.id}/activate`)
  reload?.()
}

async function loadDue() {
  const { data } = await http.get('/maintain/plan/due')
  dueList.value = data.data ?? []
}
</script>

<style scoped>
.due-alert { margin-top: 12px; }
</style>
