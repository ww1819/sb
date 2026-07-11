<template>
  <MasterDetailPage :config="config" save-url="/asset/outbound" business-type="device_outbound">
    <template #toolbar-extra="{ master, reload }">
      <el-button
        v-if="master?.id && canIssue(master)"
        type="success"
        @click="issue(master, reload)"
      >
        确认发放
      </el-button>
    </template>
  </MasterDetailPage>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const route = useRoute()
const path = computed(() => '/' + String(route.params.module) + '/' + String(route.params.page))
const config = computed(() => getPageConfig(path.value)!)

function canIssue(row: Record<string, unknown>) {
  const docStatus = String(row.doc_status ?? '')
  const status = String(row.status ?? '')
  return docStatus === 'approved' && status !== 'issued'
}

async function issue(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/asset/outbound/${form.id}/issue`)
  ElMessage.success('发放成功')
  reload?.()
}
</script>
