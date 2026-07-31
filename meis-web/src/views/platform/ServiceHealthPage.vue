<template>
  <SystemPageCard title="服务状态" subtitle="探测各微服务 Actuator 健康状态（仅平台管理员）">
    <template #actions>
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    </template>

    <div class="summary" v-if="summary">
      <el-tag type="success" effect="plain">UP {{ summary.up }}</el-tag>
      <el-tag type="danger" effect="plain">DOWN {{ summary.down }}</el-tag>
      <el-tag type="info" effect="plain">共 {{ summary.total }}</el-tag>
      <span class="checked-at">探测时间：{{ summary.checkedAt || '—' }}</span>
    </div>

    <el-table
      :data="items"
      border
      stripe
      class="system-table"
      :height="tableHeight"
      v-loading="loading"
    >
      <el-table-column prop="name" label="服务" min-width="180" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'UP' ? 'success' : 'danger'" size="small">
            {{ healthLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
      <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="url" label="探测地址" min-width="280" show-overflow-tooltip />
    </el-table>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'
import { resolveCodedLabel } from '@/i18n/resolveCodedLabel'

function healthLabel(status: unknown) {
  return resolveCodedLabel({ dictType: 'service_health', value: status })
}

const tableHeight = useSystemTableHeight()
const loading = ref(false)
const items = ref<Record<string, unknown>[]>([])
const summary = reactive({
  up: 0,
  down: 0,
  total: 0,
  checkedAt: ''
})

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/platform/service-health')
    if (data?.code !== 0 && data?.success === false) {
      ElMessage.error(data?.message || '加载失败')
      return
    }
    const payload = data?.data ?? {}
    items.value = Array.isArray(payload.items) ? payload.items : []
    summary.up = Number(payload.up ?? 0)
    summary.down = Number(payload.down ?? 0)
    summary.total = Number(payload.total ?? items.value.length)
    summary.checkedAt = String(payload.checkedAt ?? '')
  } catch (e: unknown) {
    const msg =
      e && typeof e === 'object' && 'response' in e
        ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
        : undefined
    ElMessage.error(msg || '加载服务状态失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.checked-at {
  margin-left: 8px;
  font-size: 13px;
  color: var(--meis-text-secondary, #909399);
}
</style>
