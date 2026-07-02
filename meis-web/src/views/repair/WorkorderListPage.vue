<template>
  <div>
    <CrudPage :config="config">
      <template #toolbar-extra>
        <el-button v-if="current" @click="dispatch">派工</el-button>
        <el-button v-if="current" @click="act('accept')">接单</el-button>
        <el-button v-if="current" @click="act('complete')">完工</el-button>
      </template>
    </CrudPage>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/CrudPage.vue'
import http from '@/api/http'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '维修工单', apiBase: '/repair', table: 'repair_workorder' }
const current = ref<Record<string, unknown> | null>(null)

async function dispatch() {
  if (!current.value?.id) return
  await http.post(`/repair/workorder/${current.value.id}/dispatch`, { engineerId: current.value.assigned_engineer_id })
}

async function act(type: string) {
  if (!current.value?.id) return
  if (type === 'accept') await http.post(`/repair/workorder/${current.value.id}/accept`)
  if (type === 'complete') await http.post(`/repair/workorder/${current.value.id}/complete`, { solution_description: '已完成' })
}
</script>
