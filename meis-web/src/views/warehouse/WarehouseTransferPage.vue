<template>
  <WorkflowCrudPage :config="config" save-url="/asset/transfer" business-type="asset_transfer">
    <template #toolbar-extra="{ form, reload }">
      <el-button
        v-if="form?.id && canExecute(form)"
        type="success"
        @click="execute(form, reload)"
      >
        执行调拨
      </el-button>
    </template>
  </WorkflowCrudPage>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const route = useRoute()
const path = computed(() => '/' + String(route.params.module) + '/' + String(route.params.page))
const config = computed(() => getPageConfig(path.value)!)

function canExecute(row: Record<string, unknown>) {
  return String(row.approval_status ?? '') === 'approved' && String(row.status ?? '') !== 'completed'
}

async function execute(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/asset/transfer/${form.id}/execute`)
  ElMessage.success('调拨已执行')
  reload?.()
}
</script>
