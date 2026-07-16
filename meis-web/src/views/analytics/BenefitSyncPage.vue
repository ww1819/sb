<template>
  <div class="benefit-sync-page">
    <SystemPageCard title="效益数据抓取" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="系统编码" @search="onSearch" @reset="onReset">
          <template #filters>
            <el-select v-model="systemCode" placeholder="数据源" clearable class="filter-item" @change="onSearch">
              <el-option label="HIS" value="HIS" />
              <el-option label="PACS" value="PACS" />
              <el-option label="LIS" value="LIS" />
              <el-option label="HRP" value="HRP" />
            </el-select>
          </template>
          <template #actions>
            <el-button type="primary" :loading="syncing" @click="triggerSync('HIS')">抓取HIS</el-button>
            <el-button :loading="syncing" @click="triggerSync('PACS')">抓取PACS</el-button>
            <el-button :loading="syncing" @click="triggerSync('LIS')">抓取LIS</el-button>
          </template>
        </PageFilterBar>
      </template>
      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id">
        <el-table-column prop="system_code" label="系统" width="100" />
        <el-table-column prop="task_type" label="任务类型" min-width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="result" label="结果" min-width="200" show-overflow-tooltip />
        <el-table-column prop="created_at" label="创建时间" min-width="160" />
        <el-table-column prop="finished_at" label="完成时间" min-width="160" />
      </el-table>
    </SystemPageCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'

const loading = ref(false)
const syncing = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const systemCode = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/analytics/sync/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, systemCode: systemCode.value || undefined }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { page.value = 1; load() }
function onReset() { keyword.value = ''; systemCode.value = ''; onSearch() }

async function triggerSync(system: string) {
  syncing.value = true
  try {
    const { data } = await http.post('/analytics/sync/trigger', { systemCode: system })
    ElMessage.success(`已导入 ${data.data?.imported ?? 0} 条使用记录`)
    load()
  } finally {
    syncing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.filter-item { width: 140px; }
</style>
