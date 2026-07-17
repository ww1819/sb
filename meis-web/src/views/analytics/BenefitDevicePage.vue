<template>
  <div class="benefit-device-page">
    <SystemPageCard title="单机效益分析" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="设备编码 / 名称" @search="onSearch" @reset="onReset" />
      </template>
      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="device_code" label="设备编码" min-width="120" />
        <el-table-column prop="device_name" label="设备名称" min-width="160" />
        <el-table-column prop="dept_name" label="科室" min-width="120" />
        <el-table-column prop="purchase_price" label="原值" width="100" />
        <el-table-column prop="net_value" label="净值" width="100" />
        <el-table-column prop="benefit_level" label="效益等级" width="100" />
        <el-table-column prop="net_profit" label="最新净利润" width="110" />
        <el-table-column prop="profit_rate" label="利润率" width="90" />
        <el-table-column label="最新期间" width="100">
          <template #default="{ row }">
            <span v-if="row.summary_year">{{ row.summary_year }}-{{ String(row.summary_month).padStart(2, '0') }}</span>
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>
    <AppModal v-model="visible" title="单机效益详情" size="xl">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="设备编码">{{ detail.device_code }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ detail.device_name }}</el-descriptions-item>
          <el-descriptions-item label="科室">{{ detail.dept_name }}</el-descriptions-item>
          <el-descriptions-item label="原值">{{ detail.purchase_price }}</el-descriptions-item>
          <el-descriptions-item label="净值">{{ detail.net_value }}</el-descriptions-item>
          <el-descriptions-item label="购置日期">{{ detail.purchase_date }}</el-descriptions-item>
        </el-descriptions>
        <el-tabs class="detail-tabs">
          <el-tab-pane label="月度汇总">
            <el-table :data="summaries" border size="small">
              <el-table-column label="期间">
                <template #default="{ row }">{{ row.summary_year }}-{{ String(row.summary_month).padStart(2, '0') }}</template>
              </el-table-column>
              <el-table-column prop="total_revenue" label="收入" />
              <el-table-column prop="total_cost" label="成本" />
              <el-table-column prop="net_profit" label="净利润" />
              <el-table-column prop="profit_rate" label="利润率" />
              <el-table-column prop="benefit_level" label="等级" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="使用记录">
            <el-table :data="usage" border size="small">
              <el-table-column prop="usage_date" label="日期" />
              <el-table-column prop="usage_hours" label="机时" />
              <el-table-column prop="patient_count" label="人次" />
              <el-table-column prop="revenue" label="收入" />
              <el-table-column prop="data_source" label="来源" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="成本记录">
            <el-table :data="costs" border size="small">
              <el-table-column prop="cost_date" label="日期" />
              <el-table-column prop="cost_type" label="类型" />
              <el-table-column prop="cost_amount" label="金额" />
              <el-table-column prop="description" label="说明" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <template #footer><el-button @click="visible = false">关闭</el-button></template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import AppModal from '@/components/AppModal.vue'

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const visible = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const summaries = ref<Record<string, unknown>[]>([])
const usage = ref<Record<string, unknown>[]>([])
const costs = ref<Record<string, unknown>[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/analytics/benefit/device/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { page.value = 1; load() }
function onReset() { keyword.value = ''; onSearch() }

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/analytics/benefit/device/${row.id}`)
  detail.value = data.data ?? row
  summaries.value = (data.data?.summaries as Record<string, unknown>[]) ?? []
  usage.value = (data.data?.usage as Record<string, unknown>[]) ?? []
  costs.value = (data.data?.costs as Record<string, unknown>[]) ?? []
  visible.value = true
}

onMounted(load)
</script>

<style scoped>
.detail-tabs { margin-top: 16px; }
</style>
