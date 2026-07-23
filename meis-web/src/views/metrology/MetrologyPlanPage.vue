<template>
  <WorkflowCrudPage :config="config" save-url="/metrology/plan" :enable-doc-change-log="false">
    <template #list-toolbar-extra>
      <el-button @click="loadDue">到期提醒(30天)</el-button>
    </template>
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id && form.approval_status === 'draft'" type="primary" @click="approve(form, reload)">审核通过</el-button>
      <el-button v-if="form?.id && form.approval_status === 'approved'" type="success" @click="genExec(form, reload)">生成执行单</el-button>
    </template>
    <template #drawer-extra>
      <el-alert v-if="dueList.length" :title="`近30天到期 ${dueList.length} 项`" type="warning" show-icon class="due-alert" />
    </template>
  </WorkflowCrudPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const auth = useAuthStore()
const config: PageConfig = { title: '计量计划', apiBase: '/metrology', table: 'metrology_plan' }
const dueList = ref<Record<string, unknown>[]>([])

async function approve(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/metrology/plan/${form.id}/approve`, { action: 'approve', approved_by: auth.user?.id })
  ElMessage.success('审核通过')
  reload?.()
}

async function genExec(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/metrology/plan/${form.id}/generate-execution`, { created_by: auth.user?.id })
  ElMessage.success('已生成计量执行单')
  reload?.()
}

async function loadDue() {
  const { data } = await http.get('/metrology/plan/due')
  dueList.value = data.data ?? []
}
</script>

<style scoped>
.due-alert { margin-top: 12px; }
</style>
