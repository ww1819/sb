<template>
  <div class="purchase-bidding-page">
    <SystemPageCard
      title="招标管理"
      :loading="loading"
      show-pager
      v-model:page="page"
      v-model:size="size"
      :total="total"
      @page-change="load"
    >
      <template #filterBar>
        <PageFilterBar
          v-model:keyword="keyword"
          placeholder="设备名称 / 科室 / 规格型号"
          @search="onSearch"
          @reset="onReset"
        >
          <template #actions>
            <el-button @click="exportCsv">导出</el-button>
          </template>
        </PageFilterBar>
      </template>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        stripe
        class="system-table"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" fixed="left" reserve-selection />
        <el-table-column type="index" label="序号" width="64" align="center" :index="rowSerial" />
        <el-table-column prop="device_name" label="设备名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dept_name" label="申请科室" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="100" align="right">
          <template #default="{ row }">
            <TableCellValue :field="qtyField" :value="row.quantity" />
          </template>
        </el-table-column>
        <el-table-column prop="total_price" label="总金额" min-width="120" align="right">
          <template #default="{ row }">
            <TableCellValue :field="totalField" :value="row.total_price" />
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/layout/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope } from '@/composables/useListActionScope'

const { selectedCount, selectedIds, syncFromTable, clearAll } = useCrossPageSelection()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const tableRef = ref<TableInstance>()
const selectedRowMap = ref<Map<string, Record<string, unknown>>>(new Map())

const qtyField: FieldSchema = { prop: 'quantity', label: '数量', type: 'number' }
const totalField: FieldSchema = { prop: 'total_price', label: '总金额', type: 'number' }

function rowSerial(index: number) {
  return (page.value - 1) * size.value + index + 1
}

function onSelectionChange(selection: Record<string, unknown>[]) {
  syncFromTable(selection)
  const next = new Map(selectedRowMap.value)
  const idSet = new Set(selection.map((r) => String(r.id)))
  for (const [id] of next) {
    if (!idSet.has(id)) next.delete(id)
  }
  for (const row of selection) {
    if (row.id != null) next.set(String(row.id), row)
  }
  selectedRowMap.value = next
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/purchase/bidding/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined
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
  clearAll(tableRef.value)
  selectedRowMap.value = new Map()
  load()
}

function onReset() {
  keyword.value = ''
  onSearch()
}

async function exportCsv() {
  const scope = await promptListActionScope(selectedCount.value, '导出')
  if (!scope) return
  try {
    let list: Record<string, unknown>[] = []
    if (scope === 'selected') {
      list = selectedIds()
        .map((id) => selectedRowMap.value.get(id))
        .filter((r): r is Record<string, unknown> => !!r)
    } else {
      const { data } = await http.get('/purchase/bidding/page', {
        params: {
          page: 1,
          size: 5000,
          keyword: keyword.value || undefined
        }
      })
      list = (data.data?.records ?? []) as Record<string, unknown>[]
    }
    const headers = ['设备名称', '申请科室', '规格型号', '数量', '总金额']
    const lines = [headers.join(',')]
    for (const r of list) {
      const cells = [r.device_name, r.dept_name, r.specification, r.quantity, r.total_price].map((v) => {
        const s = v == null ? '' : String(v).replace(/"/g, '""')
        return `"${s}"`
      })
      lines.push(cells.join(','))
    }
    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `招标管理_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${list.length} 条`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '导出失败')
  }
}

onMounted(() => {
  load()
})
</script>
