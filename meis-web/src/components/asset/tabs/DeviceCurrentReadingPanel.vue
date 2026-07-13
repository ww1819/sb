<template>
  <div class="device-current-reading">
    <div class="device-current-reading__toolbar">
      <el-date-picker
        v-model="dateFrom"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="开始日期"
        clearable
      />
      <el-date-picker
        v-model="dateTo"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="结束日期"
        clearable
      />
      <el-button type="primary" :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe class="system-table device-current-reading__table">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="tag_code" label="标签ID" min-width="120" show-overflow-tooltip />
      <el-table-column prop="station_code" label="基站ID" min-width="120" show-overflow-tooltip />
      <el-table-column prop="station_name" label="基站名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="current_ma" label="电流" width="100" align="right" />
      <el-table-column prop="read_at" label="读取时间" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ fmt(row.read_at) }}</template>
      </el-table-column>
      <template #empty>
        <PageEmpty description="暂无电流度数" :image-size="72" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import http from '@/api/http'
import PageEmpty from '@/components/table/PageEmpty.vue'

const props = defineProps<{
  deviceId?: string
}>()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const dateFrom = ref(today())
const dateTo = ref(today())

function today() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function fmt(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  if (!props.deviceId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const { data } = await http.get(`/power/device/${props.deviceId}/readings/page`, {
      params: {
        page: 1,
        size: 100,
        readAtFrom: dateFrom.value || undefined,
        readAtTo: dateTo.value || undefined,
        sortOrder: 'desc'
      }
    })
    rows.value = data.data?.records ?? []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.deviceId, load)
</script>

<style scoped>
.device-current-reading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 280px;
}

.device-current-reading__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-current-reading__table {
  width: 100%;
}

.device-current-reading__table :deep(.el-table__empty-block) {
  min-height: 220px;
}
</style>
