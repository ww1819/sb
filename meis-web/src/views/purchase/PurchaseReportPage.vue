<template>
  <div class="purchase-report page-view--scroll">
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6"><el-card shadow="never"><div class="kpi-title">年度预算</div><div class="kpi-value">{{ summary.totalBudget }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="kpi-title">合同金额</div><div class="kpi-value">{{ summary.totalContract }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="kpi-title">已付金额</div><div class="kpi-value">{{ summary.totalPaid }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="kpi-title">执行率%</div><div class="kpi-value">{{ summary.executionRate }}</div></el-card></el-col>
    </el-row>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="header-row">
          <span>预算执行明细</span>
          <el-input-number v-model="planYear" :min="2020" :max="2035" size="small" @change="loadBudget" />
        </div>
      </template>
      <el-table :data="budgetRows" size="small" stripe max-height="400">
        <el-table-column prop="plan_code" label="计划编号" min-width="110" />
        <el-table-column prop="dept_name" label="科室" min-width="100" />
        <el-table-column prop="total_budget" label="预算" width="100" />
        <el-table-column prop="project_amount" label="项目金额" width="100" />
        <el-table-column prop="contract_amount" label="合同金额" width="100" />
        <el-table-column prop="paid_amount" label="已付" width="90" />
        <el-table-column prop="execution_rate" label="执行率%" width="90" />
        <el-table-column prop="approval_status" label="审批" width="90" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>采购操作审计（最近50条）</template>
      <el-table :data="auditRows" size="small" stripe max-height="360">
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column prop="operator_name" label="操作人" width="100" />
        <el-table-column prop="operation_desc" label="操作" min-width="200" />
        <el-table-column prop="operation_type" label="类型" width="80" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const planYear = ref(new Date().getFullYear())
const summary = ref({ totalBudget: 0, totalContract: 0, totalPaid: 0, executionRate: 0 })
const budgetRows = ref<Record<string, unknown>[]>([])
const auditRows = ref<Record<string, unknown>[]>([])

async function loadBudget() {
  const { data } = await http.get('/purchase/report/budget', { params: { planYear: planYear.value } })
  if (data.code === 0 && data.data) {
    summary.value = {
      totalBudget: data.data.totalBudget,
      totalContract: data.data.totalContract,
      totalPaid: data.data.totalPaid,
      executionRate: data.data.executionRate
    }
    budgetRows.value = data.data.rows ?? []
  }
}

async function loadAudit() {
  const { data } = await http.get('/purchase/report/audit')
  if (data.code === 0) auditRows.value = data.data ?? []
}

onMounted(() => {
  loadBudget()
  loadAudit()
})
</script>

<style scoped>
.purchase-report { padding: 8px; }
.kpi-row { margin-bottom: 16px; }
.kpi-title { color: var(--el-text-color-secondary); font-size: 13px; }
.kpi-value { font-size: 24px; font-weight: 600; margin-top: 8px; }
.section { margin-bottom: 16px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
</style>
