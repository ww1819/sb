<template>
  <SystemPageCard title="监测记录" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
    <template #filterBar>
      <PageFilterBar v-model:keyword="keyword" placeholder="设备编码 / 名称" @search="onSearch" @reset="onReset">
        <template #filters>
          <el-input v-model="deviceCode" placeholder="设备编码" clearable class="filter-item" @change="onSearch" />
        </template>
      </PageFilterBar>
    </template>
    <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id">
      <el-table-column prop="record_date" label="日期" width="120" />
      <el-table-column prop="device_code" label="设备编码" min-width="120" />
      <el-table-column prop="device_name" label="设备名称" min-width="150" />
      <el-table-column prop="dept_name" label="科室" min-width="120" />
      <el-table-column prop="tag_code" label="标签" width="110" />
      <el-table-column prop="run_hours" label="运行(h)" width="90" />
      <el-table-column prop="idle_hours" label="待机(h)" width="90" />
      <el-table-column prop="offline_hours" label="离线(h)" width="90" />
      <el-table-column prop="avg_current" label="平均电流" width="100" />
      <el-table-column prop="peak_current" label="峰值电流" width="100" />
      <el-table-column prop="energy_kwh" label="用电(kWh)" width="100" />
    </el-table>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const deviceCode = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/power/record/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, deviceCode: deviceCode.value || undefined }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { page.value = 1; load() }
function onReset() { keyword.value = ''; deviceCode.value = ''; onSearch() }

onMounted(load)
</script>

<style scoped>
.filter-item { width: 160px; }
</style>
