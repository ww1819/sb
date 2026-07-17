<template>
  <MasterDetailPage
    ref="pageRef"
    :config="config"
    save-url="/purchase/plan"
    business-type="purchase_plan"
  >
    <template #actions-after>
      <el-button @click="printPlan">打印</el-button>
    </template>
  </MasterDetailPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printPlanDoc } from '@/utils/printDoc'
import http from '@/api/http'

const config = getPageConfig('/purchase/apply')!
const pageRef = ref<InstanceType<typeof MasterDetailPage>>()

async function printPlan() {
  const row = pageRef.value?.resolveTargetRow?.()
  if (!row?.id) {
    ElMessage.warning('请先勾选一条单据')
    return
  }
  const { data } = await http.get(`/purchase/plan/${row.id}`)
  if (data.code !== 0 || !data.data) return
  printPlanDoc(data.data)
}
</script>
