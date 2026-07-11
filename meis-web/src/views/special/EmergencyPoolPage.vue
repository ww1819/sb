<template>
  <div class="emergency-pool-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="应急设备库" name="pool">
        <CrudPage ref="poolRef" :config="poolConfig" />
      </el-tab-pane>
      <el-tab-pane label="调配记录" name="allocation">
        <SystemPageCard title="应急设备调配" :loading="allocLoading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="loadAllocations">
          <template #actions>
            <el-button type="primary" @click="openAllocate">申请调配</el-button>
          </template>
          <template #filterBar>
            <PageFilterBar v-model:keyword="keyword" placeholder="单号 / 设备名称" @search="onSearch" @reset="onReset">
              <template #filters>
                <el-select v-model="status" placeholder="状态" clearable class="filter-item" @change="onSearch">
                  <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </PageFilterBar>
          </template>
          <el-table v-loading="allocLoading" :data="allocRows" stripe class="system-table">
            <el-table-column prop="allocation_no" label="调配单号" min-width="140" />
            <el-table-column prop="device_code" label="设备编码" min-width="120" />
            <el-table-column prop="device_name" label="设备名称" min-width="140" />
            <el-table-column prop="pool_name" label="来源库" min-width="120" />
            <el-table-column prop="to_dept_name" label="调入科室" min-width="120" />
            <el-table-column prop="urgency_level" label="紧急程度" width="100">
              <template #default="{ row }">
                <TableCellValue :field="{ prop: 'urgency_level', label: '紧急程度', dictType: 'urgency_level' }" :value="row.urgency_level" />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <TableCellValue :field="{ prop: 'status', label: '状态', dictType: 'allocation_status' }" :value="row.status" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'pending'" link type="primary" @click="approve(row)">审批</el-button>
                <el-button v-if="row.status === 'approved'" link type="success" @click="returnDevice(row)">归还</el-button>
              </template>
            </el-table-column>
          </el-table>
        </SystemPageCard>
      </el-tab-pane>
    </el-tabs>

    <AppModal v-model="allocVisible" title="申请应急调配" size="lg">
      <GroupedFormFields table="emergency_device_allocation" :model="allocForm" :fields="allocFields" />
      <template #footer>
        <el-button @click="allocVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAllocate">提交</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import SystemPageCard from '@/components/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'

const poolConfig = getPageConfig('/special/emergency')!
const poolRef = ref<InstanceType<typeof CrudPage> | null>(null)
const activeTab = ref('pool')
const allocLoading = ref(false)
const allocRows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const status = ref<string>()
const allocVisible = ref(false)
const allocForm = ref<Record<string, unknown>>({})
const allocFields = getSchema('emergency_device_allocation').filter((f) => !f.readonly)
const { loadDict } = useDict()
const statusOptions = ref<{ label: string; value: string }[]>([])

async function loadAllocations() {
  allocLoading.value = true
  try {
    const { data } = await http.get('/special/emergency/allocations/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        status: status.value || undefined
      }
    })
    allocRows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    allocLoading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadAllocations()
}

function onReset() {
  keyword.value = ''
  status.value = undefined
  onSearch()
}

function openAllocate() {
  allocForm.value = { urgency_level: 'normal' }
  allocVisible.value = true
}

async function submitAllocate() {
  await http.post('/special/emergency/allocate', allocForm.value)
  ElMessage.success('调配申请已提交')
  allocVisible.value = false
  loadAllocations()
}

async function approve(row: Record<string, unknown>) {
  await http.post(`/special/emergency/allocate/${row.id}/approve`)
  ElMessage.success('已审批通过')
  loadAllocations()
}

async function returnDevice(row: Record<string, unknown>) {
  await http.post(`/special/emergency/allocate/${row.id}/return`)
  ElMessage.success('已归还')
  loadAllocations()
}

onMounted(async () => {
  statusOptions.value = await loadDict('allocation_status')
  loadAllocations()
})
</script>

<style scoped>
.emergency-pool-page {
  height: 100%;
}
</style>
