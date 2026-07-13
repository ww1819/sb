<template>
  <div class="maintain-query-page">
    <SystemPageCard
      title="保养记录查询"
      :loading="loading"
      show-pager
      v-model:page="page"
      v-model:size="size"
      :total="total"
      @page-change="load"
    >
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="设备名称 / 执行单号" @search="onSearch" @reset="onReset">
          <template #filters>
            <el-input v-model="deviceCode" placeholder="设备编码" clearable class="filter-item" @change="onSearch" />
            <el-select v-model="resultStatus" placeholder="保养结果" clearable class="filter-item" @change="onSearch">
              <el-option label="合格" value="pass" />
              <el-option label="不合格" value="fail" />
            </el-select>
          </template>
        </PageFilterBar>
      </template>

      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="execution_no" label="执行单号" min-width="130" />
        <el-table-column prop="device_code" label="设备编码" min-width="120" />
        <el-table-column prop="device_name" label="设备名称" min-width="160" />
        <el-table-column prop="dept_name" label="科室" min-width="120" />
        <el-table-column prop="template_name" label="保养模板" min-width="140" />
        <el-table-column prop="maintenance_level_name" label="保养级别" width="100" />
        <el-table-column prop="overall_result" label="结果" width="80" />
        <el-table-column prop="completed_at" label="完成时间" min-width="160" />
      </el-table>
    </SystemPageCard>

    <AppModal v-model="visible" title="保养记录详情" size="lg">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="执行单号">{{ detail.execution_no }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detail.device_code }} {{ detail.device_name }}</el-descriptions-item>
          <el-descriptions-item label="模板">{{ detail.template_name }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.overall_result }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="results" border size="small" class="result-table">
          <el-table-column prop="item_name" label="保养项目" />
          <el-table-column prop="item_content" label="保养内容" show-overflow-tooltip />
          <el-table-column prop="result_status" label="结果" width="80" />
          <el-table-column prop="result_value" label="实测值" width="120" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import SystemPageCard from '@/components/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'
import AppModal from '@/components/AppModal.vue'

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const deviceCode = ref('')
const resultStatus = ref('')
const visible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const results = ref<Record<string, unknown>[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/maintain/query/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, deviceCode: deviceCode.value || undefined, resultStatus: resultStatus.value || undefined }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  keyword.value = ''
  deviceCode.value = ''
  resultStatus.value = ''
  onSearch()
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/maintain/query/${row.id}`)
  detail.value = data.data ?? row
  results.value = (data.data?.results as Record<string, unknown>[]) ?? []
  visible.value = true
}

onMounted(load)
</script>

<style scoped>
.result-table { margin-top: 16px; }
.filter-item { width: 160px; }
</style>
