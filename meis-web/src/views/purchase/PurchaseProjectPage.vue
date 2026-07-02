<template>
  <CrudPage :config="config">
    <template #toolbar-extra>
      <el-button v-if="selected" type="warning" @click="transition('bidding')">启动招标</el-button>
    </template>
  </CrudPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/CrudPage.vue'
import http from '@/api/http'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '采购项目', apiBase: '/purchase', table: 'purchase_project' }
const selected = ref<Record<string, unknown> | null>(null)

async function transition(status: string) {
  if (!selected.value?.id) return
  await http.post(`/purchase/project/${selected.value.id}/transition`, { status })
}
</script>
