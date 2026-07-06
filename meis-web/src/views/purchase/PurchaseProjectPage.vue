<template>
  <WorkflowCrudPage ref="pageRef" :config="config" save-url="/purchase/project">
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id" @click="transition(form, 'bidding', reload)">启动招标</el-button>
      <el-button v-if="form?.id" @click="transition(form, 'awarded', reload)">定标</el-button>
      <el-button v-if="form?.id" @click="transition(form, 'closed', reload)">关闭项目</el-button>
    </template>
  </WorkflowCrudPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import http from '@/api/http'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '采购项目', apiBase: '/purchase', table: 'purchase_project' }
const pageRef = ref<InstanceType<typeof WorkflowCrudPage> | null>(null)

async function transition(form: Record<string, unknown>, status: string, reload?: () => void) {
  if (!form?.id) return
  await http.post(`/purchase/project/${form.id}/transition`, { status })
  reload?.()
}
</script>
