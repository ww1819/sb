<template>
  <div class="power-status-page">
    <SystemPageCard title="设备运行状态" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="设备编码 / 名称" @search="onSearch" @reset="onReset">
          <template #filters>
            <el-select v-model="workState" placeholder="运行状态" clearable class="filter-item" @change="onSearch">
              <el-option label="运行中" value="running" />
              <el-option label="待机" value="idle" />
              <el-option label="离线" value="offline" />
              <el-option label="告警" value="alarm" />
            </el-select>
          </template>
          <template #actions>
            <el-button type="primary" :loading="collecting" @click="collect">采集状态</el-button>
          </template>
        </PageFilterBar>
      </template>
      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id">
        <el-table-column prop="device_code" label="设备编码" min-width="120" />
        <el-table-column prop="device_name" label="设备名称" min-width="150" />
        <el-table-column prop="dept_name" label="科室" min-width="120" />
        <el-table-column prop="tag_code" label="标签" width="110" />
        <el-table-column prop="station_name" label="基站" min-width="120" />
        <el-table-column prop="current_amp" label="电流(A)" width="100" />
        <el-table-column prop="voltage" label="电压(V)" width="90" />
        <el-table-column prop="power_watt" label="功率(W)" width="100" />
        <el-table-column prop="work_state" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="stateType(row.work_state)" size="small">{{ stateLabel(row.work_state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="collected_at" label="采集时间" min-width="160" />
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
const collecting = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const workState = ref('')

const stateMap: Record<string, string> = { running: '运行中', idle: '待机', offline: '离线', alarm: '告警' }
const typeMap: Record<string, string> = { running: 'success', idle: 'warning', offline: 'info', alarm: 'danger' }

function stateLabel(v: unknown) { return stateMap[String(v)] ?? String(v ?? '') }
function stateType(v: unknown) { return typeMap[String(v)] ?? 'info' }

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/power/status/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, workState: workState.value || undefined }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { page.value = 1; load() }
function onReset() { keyword.value = ''; workState.value = ''; onSearch() }

async function collect() {
  collecting.value = true
  try {
    const { data } = await http.post('/power/status/collect')
    ElMessage.success(`已采集 ${data.data?.collected ?? 0} 个标签状态`)
    load()
  } finally {
    collecting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.filter-item { width: 140px; }
</style>
