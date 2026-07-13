<template>
  <MasterDetailPage :config="config" save-url="/asset/return" business-type="device_return">
    <template #toolbar-extra="{ master, reload }">
      <el-button
        v-if="master?.id && canComplete(master)"
        type="success"
        @click="complete(master, reload)"
      >
        确认退库
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

function canComplete(row: Record<string, unknown>) {
  return String(row.status ?? '') !== 'returned' && String(row.approval_status ?? '') === 'approved'
}

async function complete(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/asset/return/${form.id}/complete`)
  ElMessage.success('退库成功')
  reload?.()
}
</script>
