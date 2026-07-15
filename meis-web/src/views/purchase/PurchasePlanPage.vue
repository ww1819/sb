<template>
  <MasterDetailPage
    ref="pageRef"
    :config="config"
    save-url="/purchase/plan"
    business-type="purchase_plan"
  >
    <template #toolbar-extra>
      <el-button v-if="selectedId" @click="printPlan">打印计划</el-button>
    </template>
  </MasterDetailPage>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printPlanDoc } from '@/utils/printDoc'
import http from '@/api/http'

const config = getPageConfig('/purchase/plan')!
const pageRef = ref<InstanceType<typeof MasterDetailPage>>()
const selectedId = computed(() => pageRef.value?.selectedId)

async function printPlan() {
  const id = selectedId.value
  if (!id) return
  const { data } = await http.get(`/purchase/plan/${id}`)
  if (data.code !== 0 || !data.data) return
  const p = data.data
  printPlanDoc(p)
}
</script>
