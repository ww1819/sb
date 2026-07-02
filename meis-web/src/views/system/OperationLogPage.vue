<template>
  <SystemPageCard title="操作日志" subtitle="查看系统操作审计记录" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="onPageChange">
    <template #filter>
      <el-input v-model="filters.module" clearable placeholder="模块" style="width:140px" />
      <el-input v-model="filters.userId" clearable placeholder="用户ID" style="width:200px" />
      <el-date-picker v-model="filters.startDate" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" />
      <el-date-picker v-model="filters.endDate" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </template>
    <el-table :data="rows" border stripe class="system-table">
      <el-table-column prop="created_at" label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column prop="module_name" label="模块" width="120" />
      <el-table-column prop="operation_desc" label="操作" show-overflow-tooltip />
      <el-table-column prop="request_method" label="方法" width="80" />
      <el-table-column prop="request_url" label="URL" show-overflow-tooltip />
      <el-table-column prop="ip_address" label="IP" width="130" />
      <el-table-column label="详情" width="80">
        <template #default="{ row }">
          <el-button link @click="showDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-drawer v-model="detailVisible" title="日志详情" size="480px">
      <pre class="detail-json">{{ detailText }}</pre>
    </el-drawer>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { fetchPage, usePagedList } from '@/composables/usePagedList'

const filters = reactive({ module: '', userId: '', startDate: '', endDate: '' })
const detailVisible = ref(false)
const detailText = ref('')

const { rows, total, page, size, loading, load, search, onPageChange } = usePagedList((params) => {
  if (filters.module) params.set('module', filters.module)
  if (filters.userId) params.set('userId', filters.userId)
  if (filters.startDate) params.set('startDate', filters.startDate)
  if (filters.endDate) params.set('endDate', filters.endDate)
  return fetchPage('/system/logs/page', params)
})

onMounted(load)

function resetFilters() {
  filters.module = ''
  filters.userId = ''
  filters.startDate = ''
  filters.endDate = ''
  search()
}

function showDetail(row: any) {
  detailText.value = JSON.stringify(row, null, 2)
  detailVisible.value = true
}

function formatTime(v: string) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<style scoped>
.detail-json { font-size: 12px; white-space: pre-wrap; }
</style>
