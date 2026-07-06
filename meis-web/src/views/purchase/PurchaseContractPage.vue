<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail">
      <template #toolbar-extra>
        <el-button v-if="contract?.id" type="warning" @click="save">保存合同</el-button>
        <el-button v-if="contract?.id" type="primary" @click="submit">提交审批</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" title="采购合同 详情" size="xl">
      <el-tabs v-if="contract">
        <el-tab-pane label="合同信息">
          <GroupedFormFields :table="config.table" :model="contract" />
        </el-tab-pane>
        <el-tab-pane label="付款明细">
          <el-table :data="payments" border max-height="360">
            <el-table-column v-for="f in paymentFields" :key="f.prop" :label="f.label" :min-width="120">
              <template #default="{ row }">
                <FieldRenderer v-model="row[f.prop]" :field="f" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="payments.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button class="mt-8" @click="addPayment">添加付款计划</el-button>
        </el-tab-pane>
      </el-tabs>
      <ApprovalPanel
        v-if="contract?.id"
        business-type="purchase_contract"
        :business-id="String(contract.id)"
        @changed="reload"
      />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="warning" @click="save">保存合同</el-button>
        <el-button type="primary" @click="submit">提交审批</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getDetailFields } from '@/config/pageSchemas'

const auth = useAuthStore()
const config: PageConfig = { title: '采购合同', apiBase: '/purchase', table: 'purchase_contract' }
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const contract = ref<Record<string, unknown> | null>(null)
const payments = ref<Record<string, unknown>[]>([])
const paymentFields = getDetailFields('contract_payment')

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/purchase/contract/${row.id}`)
  contract.value = data.data
  payments.value = (data.data?.payments as Record<string, unknown>[]) ?? []
  visible.value = true
}

function addPayment() {
  payments.value.push({ payment_no: 'PAY' + Date.now(), payment_stage: 'advance', payment_amount: 0, status: 'pending' })
}

async function save() {
  if (!contract.value) return
  await http.post('/purchase/contract', { ...contract.value, payments: payments.value })
  visible.value = false
  crudRef.value?.load()
}

async function submit() {
  if (!contract.value?.id) return
  await http.post(`/purchase/contract/${contract.value.id}/submit`, { applicantId: auth.user?.userId })
  await reload()
}

async function reload() {
  if (!contract.value?.id) return
  const { data } = await http.get(`/purchase/contract/${contract.value.id}`)
  contract.value = data.data
  payments.value = (data.data?.payments as Record<string, unknown>[]) ?? []
  crudRef.value?.load()
}
</script>

<style scoped>
.mt-8 { margin-top: 8px; }
</style>
