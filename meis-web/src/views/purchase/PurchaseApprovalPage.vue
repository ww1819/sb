<template>
  <div class="purchase-approval-page">
    <SystemPageCard title="采购审批" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="单号 / 标题" @search="onSearch" @reset="onReset">
          <template #filters>
            <el-select v-model="businessType" placeholder="业务类型" clearable class="filter-item" @change="onSearch">
              <el-option label="采购计划" value="purchase_plan" />
              <el-option label="采购项目" value="purchase_project" />
              <el-option label="采购合同" value="purchase_contract" />
              <el-option label="安装验收" value="purchase_acceptance" />
              <el-option label="合同付款" value="contract_payment" />
            </el-select>
            <el-select v-model="status" placeholder="状态" clearable class="filter-item" @change="onSearch">
              <el-option label="待审批" value="pending" />
              <el-option label="已通过" value="approved" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
          </template>
        </PageFilterBar>
      </template>
      <el-alert v-if="summary.totalPending" :title="`待审批 ${summary.totalPending} 项`" type="info" show-icon class="summary-alert" />
      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="business_no" label="业务单号" min-width="130" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="business_type" label="类型" width="120">
          <template #default="{ row }">{{ typeLabel(row.business_type) }}</template>
        </el-table-column>
        <el-table-column prop="applicant_name" label="申请人" width="100" />
        <el-table-column prop="current_node_name" label="当前节点" width="120" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="created_at" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" link type="primary" @click="openDetail(row)">审批</el-button>
            <el-button v-else link @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>

    <AppModal v-model="visible" title="审批详情" size="lg">
      <template v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="业务单号">{{ current.business_no }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(current.business_type) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ current.applicant_name }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ current.status }}</el-descriptions-item>
        </el-descriptions>
        <ApprovalPanel
          v-if="current.business_type && current.business_id"
          :business-type="String(current.business_type)"
          :business-id="String(current.business_id)"
          @changed="onApprovalChanged"
        />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="current?.business_type && current?.business_id" type="primary" @click="goBusiness">打开业务单据</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import SystemPageCard from '@/components/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'
import AppModal from '@/components/AppModal.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'

const router = useRouter()
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const businessType = ref('')
const status = ref('pending')
const visible = ref(false)
const current = ref<Record<string, unknown> | null>(null)
const summary = reactive<Record<string, number>>({ totalPending: 0 })

const typeMap: Record<string, string> = {
  purchase_plan: '采购计划',
  purchase_project: '采购项目',
  purchase_contract: '采购合同',
  purchase_acceptance: '安装验收',
  contract_payment: '合同付款'
}

function typeLabel(t: unknown) {
  return typeMap[String(t)] ?? String(t)
}

async function loadSummary() {
  const { data } = await http.get('/purchase/approval/summary')
  Object.assign(summary, data.data ?? {})
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/purchase/approval/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        businessType: businessType.value || undefined,
        status: status.value || undefined
      }
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
  businessType.value = ''
  status.value = 'pending'
  onSearch()
}

function openDetail(row: Record<string, unknown>) {
  current.value = row
  visible.value = true
}

function onApprovalChanged() {
  load()
  loadSummary()
}

function goBusiness() {
  if (!current.value) return
  const type = String(current.value.business_type)
  const pathMap: Record<string, string> = {
    purchase_plan: '/purchase/apply',
    purchase_project: '/purchase/project',
    purchase_contract: '/purchase/contract',
    purchase_acceptance: '/purchase/acceptance'
  }
  const path = pathMap[type]
  if (path) router.push(path)
  visible.value = false
}

onMounted(() => {
  loadSummary()
  load()
})
</script>

<style scoped>
.filter-item { width: 160px; }
.summary-alert { margin-bottom: 12px; }
</style>
