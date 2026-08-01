<template>
  <SystemPageCard title="设备盘点报表" :loading="loading">
    <template #filterBar>
      <PageFilterBar
        v-model:keyword="keyword"
        placeholder="单号/名称"
        @search="load"
        @reset="onReset"
      >
        <template #filters>
          <el-input-number
            v-model="checkYear"
            :controls="false"
            placeholder="年度"
            class="year-input"
            @change="load"
          />
          <RefSelect
            v-model="deptId"
            link-table="department"
            placeholder="科室"
            @update:model-value="load"
          />
          <el-select v-model="checkType" clearable placeholder="盘点类型" style="width: 140px" @change="load">
            <el-option v-for="o in checkTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
            <el-option label="计划中" value="planning" />
            <el-option label="盘点中" value="in_progress" />
            <el-option label="已完成" value="completed" />
          </el-select>
        </template>
        <template #actions>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-button @click="exportCsv">导出</el-button>
        </template>
      </PageFilterBar>
    </template>

    <el-table :data="rows" border stripe height="100%" empty-text="暂无盘点数据">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="check_no" label="盘点单号" min-width="120" />
      <el-table-column prop="check_name" label="盘点名称" min-width="140" />
      <el-table-column prop="check_year" label="年度" width="80" />
      <el-table-column prop="check_type" label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.check_type) }}</template>
      </el-table-column>
      <el-table-column prop="dept_name" label="科室" min-width="120" />
      <el-table-column prop="total_count" label="应盘" width="80" align="right" />
      <el-table-column prop="checked_count" label="已盘" width="80" align="right" />
      <el-table-column prop="matched_count" label="相符" width="80" align="right" />
      <el-table-column prop="mismatch_count" label="不符" width="80" align="right" />
      <el-table-column prop="missing_count" label="盘亏" width="80" align="right" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="audit_status" label="审核" width="90">
        <template #default="{ row }">{{ auditLabel(row.audit_status) }}</template>
      </el-table-column>
      <el-table-column prop="actual_end_at" label="完成时间" min-width="160" />
    </el-table>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import { useDict } from '@/composables/useDict'

const { loadDict } = useDict()
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const keyword = ref('')
const checkYear = ref<number | undefined>(new Date().getFullYear())
const deptId = ref<string | undefined>()
const checkType = ref<string | undefined>('dept')
const status = ref<string | undefined>()
const checkTypeOptions = ref<{ value: string; label: string }[]>([])

const statusMap: Record<string, string> = {
  planning: '计划中',
  in_progress: '盘点中',
  completed: '已完成'
}
const auditMap: Record<string, string> = {
  pending: '待审核',
  approved: '已审核',
  rejected: '已驳回'
}

function typeLabel(v: unknown) {
  const hit = checkTypeOptions.value.find((o) => o.value === String(v ?? ''))
  return hit?.label ?? (v == null || v === '' ? '-' : String(v))
}
function statusLabel(v: unknown) {
  return statusMap[String(v ?? '')] ?? (v == null ? '-' : String(v))
}
function auditLabel(v: unknown) {
  return auditMap[String(v ?? '')] ?? (v == null ? '-' : String(v))
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/asset/inventory/report', {
      params: {
        checkYear: checkYear.value,
        deptId: deptId.value || undefined,
        checkType: checkType.value || undefined,
        status: status.value || undefined
      }
    })
    let list = (data.data ?? []) as Record<string, unknown>[]
    const kw = keyword.value.trim().toLowerCase()
    if (kw) {
      list = list.filter((r) => {
        const no = String(r.check_no ?? '').toLowerCase()
        const name = String(r.check_name ?? '').toLowerCase()
        return no.includes(kw) || name.includes(kw)
      })
    }
    rows.value = list
  } finally {
    loading.value = false
  }
}

function onReset() {
  keyword.value = ''
  checkYear.value = new Date().getFullYear()
  deptId.value = undefined
  checkType.value = 'dept'
  status.value = undefined
  load()
}

function exportCsv() {
  const headers = [
    '盘点单号',
    '盘点名称',
    '年度',
    '类型',
    '科室',
    '应盘',
    '已盘',
    '相符',
    '不符',
    '盘亏',
    '状态',
    '审核'
  ]
  const lines = [headers.join(',')]
  for (const r of rows.value) {
    lines.push(
      [
        r.check_no,
        r.check_name,
        r.check_year,
        typeLabel(r.check_type),
        r.dept_name,
        r.total_count,
        r.checked_count,
        r.matched_count,
        r.mismatch_count,
        r.missing_count,
        statusLabel(r.status),
        auditLabel(r.audit_status)
      ]
        .map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`)
        .join(',')
    )
  }
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `盘点报表_${checkYear.value ?? 'all'}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(async () => {
  checkTypeOptions.value = (await loadDict('check_type')) ?? []
  await load()
})
</script>

<style scoped>
.year-input {
  width: 110px;
}
</style>
