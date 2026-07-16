<template>
  <SystemPageCard
    title="维修工程师管理"
    subtitle="从系统用户中维护维修工程师岗位"
    :loading="loading"
    show-search
    @search="load"
    @reset="resetSearch"
    v-model:keyword="keyword"
  >
    <template #filter>
      <RefSelect v-model="deptId" link-table="department" placeholder="科室" class="filter-ref" @update:model-value="onFilterChange" />
      <el-select v-model="workload" placeholder="负责工单" clearable class="filter-select" @change="onFilterChange">
        <el-option label="有在办工单" value="has" />
        <el-option label="无在办工单" value="none" />
      </el-select>
    </template>
    <template #actions>
      <el-button type="primary" @click="openAdd">新增工程师</el-button>
    </template>

    <el-table :data="list" border stripe class="system-table">
      <el-table-column prop="employee_no" label="工号" width="120" />
      <el-table-column prop="real_name" label="姓名" min-width="120" />
      <el-table-column prop="dept_name" label="科室" min-width="140" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="workorder_count" label="负责工单" width="100" align="center" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRecords(row)">查看维修记录</el-button>
          <el-button link type="warning" @click="revoke(row)">设为非工程师</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </SystemPageCard>

  <AppModal v-model="addVisible" title="新增维修工程师" size="lg">
    <div class="add-toolbar">
      <el-input v-model="candidateKeyword" placeholder="搜索姓名/工号/账号" clearable style="max-width: 280px" @keyup.enter="loadCandidates" />
      <el-button @click="loadCandidates">搜索</el-button>
    </div>
    <el-table
      ref="candidateTableRef"
      :data="candidates"
      border
      stripe
      row-key="id"
      max-height="360"
      @selection-change="onCandidateSelect"
    >
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column prop="employee_no" label="工号" width="120" />
      <el-table-column prop="real_name" label="姓名" min-width="120" />
      <el-table-column prop="dept_name" label="科室" min-width="140" />
      <el-table-column prop="phone" label="电话" width="130" />
    </el-table>
    <div class="pager">
      <el-pagination
        v-model:current-page="candidatePage"
        v-model:page-size="candidateSize"
        :total="candidateTotal"
        layout="total, prev, pager, next"
        @current-change="loadCandidates"
        @size-change="loadCandidates"
      />
    </div>
    <template #footer>
      <el-button @click="addVisible = false">取消</el-button>
      <el-button type="primary" :disabled="selectedCount === 0" @click="confirmAdd">确认添加</el-button>
    </template>
  </AppModal>

  <AppModal v-model="recordsVisible" :title="recordsTitle" size="xl">
    <el-table :data="records" border stripe max-height="420" v-loading="recordsLoading">
      <el-table-column prop="wo_no" label="工单号" width="160" />
      <el-table-column prop="device_name" label="设备" min-width="140" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <TableCellValue :field="{ prop: 'status', dictType: 'wo_status' }" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="report_time" label="报修时间" width="170" />
      <el-table-column prop="fault_description" label="故障描述" min-width="180" show-overflow-tooltip />
    </el-table>
    <template #footer>
      <el-button @click="recordsVisible = false">关闭</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import AppModal from '@/components/AppModal.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'

const loading = ref(false)
const keyword = ref('')
const deptId = ref('')
const workload = ref('')
const list = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const addVisible = ref(false)
const candidateKeyword = ref('')
const candidates = ref<Record<string, unknown>[]>([])
const candidatePage = ref(1)
const candidateSize = ref(10)
const candidateTotal = ref(0)
const candidateTableRef = ref()
const {
  selectedCount,
  syncFromTable,
  selectedIds,
  clearAll
} = useCrossPageSelection()

const recordsVisible = ref(false)
const recordsTitle = ref('维修记录')
const records = ref<Record<string, unknown>[]>([])
const recordsLoading = ref(false)
const recordsUserId = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/repair/engineer/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        deptId: deptId.value || undefined,
        workload: workload.value || undefined
      }
    })
    const pr = data.data
    list.value = pr?.records ?? pr?.list ?? []
    total.value = Number(pr?.total ?? 0)
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  keyword.value = ''
  deptId.value = ''
  workload.value = ''
  page.value = 1
  load()
}

function onFilterChange() {
  page.value = 1
  load()
}

function openAdd() {
  onClearCandidateSelection()
  candidateKeyword.value = ''
  candidatePage.value = 1
  addVisible.value = true
  loadCandidates()
}

function onCandidateSelect(selection: Record<string, unknown>[]) {
  syncFromTable(selection)
}

function onClearCandidateSelection() {
  clearAll(candidateTableRef.value)
}

async function loadCandidates() {
  const { data } = await http.get('/repair/engineer/candidates/page', {
    params: {
      page: candidatePage.value,
      size: candidateSize.value,
      keyword: candidateKeyword.value || undefined
    }
  })
  const pr = data.data
  candidates.value = pr?.records ?? pr?.list ?? []
  candidateTotal.value = Number(pr?.total ?? 0)
}

async function confirmAdd() {
  const ids = selectedIds()
  if (!ids.length) return
  const { data } = await http.post('/repair/engineer/batch-add', { userIds: ids })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '添加失败')
    return
  }
  ElMessage.success(`已添加 ${data.data?.updated ?? ids.length} 名维修工程师`)
  addVisible.value = false
  onClearCandidateSelection()
  await load()
}

async function revoke(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`确认将「${row.real_name}」设为非维修工程师？`, '撤销岗位', { type: 'warning' })
  const { data } = await http.post(`/repair/engineer/${row.id}/revoke`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '操作失败')
    return
  }
  ElMessage.success('已撤销维修工程师岗位')
  await load()
}

async function openRecords(row: Record<string, unknown>) {
  recordsUserId.value = String(row.id)
  recordsTitle.value = `${row.real_name} · 维修记录`
  recordsVisible.value = true
  recordsLoading.value = true
  try {
    const { data } = await http.get('/repair/workorder/page', {
      params: { page: 1, size: 200, assignedUserId: recordsUserId.value }
    })
    const pr = data.data
    records.value = pr?.records ?? pr?.list ?? []
  } finally {
    recordsLoading.value = false
  }
}

load()
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.add-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.filter-ref,
.filter-select {
  width: 180px;
}
</style>
