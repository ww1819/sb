<template>
  <div class="purchase-trace page-view--scroll">
    <el-card shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="追溯关键词">
          <el-input v-model="keyword" placeholder="计划/合同/业务链编号" clearable style="width:280px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询全链路</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="searched && !chains.length" description="未找到匹配业务链" />

    <div v-for="(chain, idx) in chains" :key="idx" class="chain-block">
      <el-card shadow="never">
        <template #header>
          <span>业务链：{{ chain.plan?.business_chain_no || chain.plan?.plan_code }}</span>
        </template>

        <el-descriptions :column="3" border size="small" title="采购计划">
          <el-descriptions-item label="计划编号">{{ chain.plan?.plan_code }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">{{ chain.plan?.approval_status }}</el-descriptions-item>
          <el-descriptions-item label="预算">{{ chain.plan?.total_budget }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">采购项目</h4>
        <el-table :data="chain.projects || []" size="small" stripe>
          <el-table-column prop="project_code" label="项目编号" />
          <el-table-column prop="project_name" label="项目名称" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="total_amount" label="金额" width="100" />
        </el-table>

        <h4 class="section-title">采购合同</h4>
        <el-table :data="chain.contracts || []" size="small" stripe>
          <el-table-column prop="contract_code" label="合同编号" />
          <el-table-column prop="contract_name" label="合同名称" />
          <el-table-column prop="approval_status" label="审批" width="90" />
          <el-table-column prop="payment_progress" label="付款%" width="80" />
        </el-table>

        <h4 class="section-title">安装验收</h4>
        <el-table :data="chain.acceptances || []" size="small" stripe>
          <el-table-column prop="acceptance_no" label="验收单号" />
          <el-table-column prop="acceptance_status" label="状态" width="90" />
          <el-table-column prop="approval_status" label="审批" width="90" />
        </el-table>

        <h4 class="section-title">设备入库</h4>
        <el-table :data="chain.entries || []" size="small" stripe>
          <el-table-column prop="entry_no" label="入库单号" />
          <el-table-column prop="business_chain_no" label="业务链编号" />
          <el-table-column prop="status" label="状态" width="90" />
        </el-table>

        <h4 class="section-title">台账设备</h4>
        <el-table :data="chain.devices || []" size="small" stripe>
          <el-table-column prop="device_code" label="设备编码" />
          <el-table-column prop="device_name" label="设备名称" />
          <el-table-column prop="device_status" label="状态" width="90" />
        </el-table>

        <h4 class="section-title">合同付款</h4>
        <el-table :data="chain.payments || []" size="small" stripe>
          <el-table-column prop="payment_no" label="付款单号" />
          <el-table-column prop="payment_stage" label="阶段" width="90" />
          <el-table-column prop="payment_amount" label="金额" width="100" />
          <el-table-column prop="status" label="状态" width="90" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import http from '@/api/http'

const keyword = ref('')
const chains = ref<Record<string, unknown>[]>([])
const searched = ref(false)

async function search() {
  if (!keyword.value.trim()) return
  const { data } = await http.get('/purchase/trace', { params: { keyword: keyword.value.trim() } })
  searched.value = true
  chains.value = data.code === 0 ? (data.data?.chains ?? []) : []
}
</script>

<style scoped>
.purchase-trace { padding: 8px; }
.chain-block { margin-top: 16px; }
.section-title { margin: 16px 0 8px; font-size: 14px; color: var(--el-text-color-secondary); }
</style>
