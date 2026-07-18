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
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBid(row)">招标</el-button>
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>

    <AppModal v-model="bidVisible" title="招标" size="xl">
      <div class="bid-split">
        <div class="bid-left">
          <div class="bid-panel-title">产品明细</div>
          <el-table :data="bidLeftRows" border size="small" class="bid-left-table">
            <el-table-column type="index" label="序号" width="52" align="center" />
            <el-table-column prop="device_name" label="设备名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="dept_name" label="申请科室" min-width="100" show-overflow-tooltip />
            <el-table-column prop="specification" label="规格型号" min-width="120" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="80" align="right">
              <template #default="{ row }">
                <TableCellValue :field="qtyField" :value="row.quantity" />
              </template>
            </el-table-column>
            <el-table-column prop="total_price" label="总金额" width="110" align="right">
              <template #default="{ row }">
                <TableCellValue :field="totalField" :value="row.total_price" />
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="bid-right">
          <div class="bid-panel-head">
            <div class="bid-panel-title">供应商信息</div>
            <el-button type="primary" link @click="addSupplierRow">新增行</el-button>
          </div>
          <el-table :data="supplierRows" border size="small" class="bid-supplier-table">
            <el-table-column type="index" label="序号" width="52" align="center" />
            <el-table-column label="供应商名称" min-width="140">
              <template #default="{ row }">
                <el-input v-model="row.supplier_name" placeholder="供应商名称" />
              </template>
            </el-table-column>
            <el-table-column label="联系人" width="110">
              <template #default="{ row }">
                <el-input v-model="row.contact_person" placeholder="联系人" />
              </template>
            </el-table-column>
            <el-table-column label="联系电话" width="130">
              <template #default="{ row }">
                <el-input v-model="row.contact_phone" placeholder="联系电话" />
              </template>
            </el-table-column>
            <el-table-column label="品牌" width="110">
              <template #default="{ row }">
                <el-input v-model="row.brand" placeholder="品牌" />
              </template>
            </el-table-column>
            <el-table-column label="规格型号" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.specification" placeholder="规格型号" />
              </template>
            </el-table-column>
            <el-table-column label="最终金额" width="120">
              <template #default="{ row }">
                <el-input v-model="row.final_amount" placeholder="最终金额" />
              </template>
            </el-table-column>
            <el-table-column label="质保期" width="100">
              <template #default="{ row }">
                <el-input v-model="row.warranty_period" placeholder="质保期" />
              </template>
            </el-table-column>
            <el-table-column label="优惠条款" min-width="140">
              <template #default="{ row }">
                <el-input v-model="row.preferential_terms" placeholder="优惠条款" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeSupplierRow($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="bidVisible = false">取消</el-button>
        <el-button type="primary" :loading="bidSubmitting" @click="submitBid">确认</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import AppModal from '@/components/AppModal.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope } from '@/composables/useListActionScope'

type SupplierRow = {
  id?: string
  supplier_name: string
  contact_person: string
  contact_phone: string
  brand: string
  specification: string
  final_amount: string
  warranty_period: string
  preferential_terms: string
}

const { selectedCount, selectedIds, syncFromTable, clearAll } = useCrossPageSelection()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const tableRef = ref<TableInstance>()
const selectedRowMap = ref<Map<string, Record<string, unknown>>>(new Map())

const bidVisible = ref(false)
const bidSubmitting = ref(false)
const bidRow = ref<Record<string, unknown> | null>(null)
const supplierRows = ref<SupplierRow[]>([])

const qtyField: FieldSchema = { prop: 'quantity', label: '数量', type: 'number' }
const totalField: FieldSchema = { prop: 'total_price', label: '总金额', type: 'number' }

const bidLeftRows = computed(() => (bidRow.value ? [bidRow.value] : []))

function emptySupplier(): SupplierRow {
  return {
    supplier_name: '',
    contact_person: '',
    contact_phone: '',
    brand: '',
    specification: '',
    final_amount: '',
    warranty_period: '',
    preferential_terms: ''
  }
}

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

async function openBid(row: Record<string, unknown>) {
  if (!row.id) return
  bidRow.value = row
  bidVisible.value = true
  supplierRows.value = []
  try {
    const { data } = await http.get(`/purchase/bidding/approved-items/${row.id}/suppliers`)
    const list = (data.data ?? []) as Record<string, unknown>[]
    supplierRows.value = list.length
      ? list.map((r) => ({
          id: r.id != null ? String(r.id) : undefined,
          supplier_name: String(r.supplier_name ?? ''),
          contact_person: String(r.contact_person ?? ''),
          contact_phone: String(r.contact_phone ?? ''),
          brand: String(r.brand ?? ''),
          specification: String(r.specification ?? ''),
          final_amount: r.final_amount == null ? '' : String(r.final_amount),
          warranty_period: String(r.warranty_period ?? ''),
          preferential_terms: String(r.preferential_terms ?? '')
        }))
      : [emptySupplier()]
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载供应商明细失败')
    bidVisible.value = false
  }
}

function addSupplierRow() {
  supplierRows.value.push(emptySupplier())
}

function removeSupplierRow(index: number) {
  supplierRows.value.splice(index, 1)
  if (!supplierRows.value.length) {
    supplierRows.value.push(emptySupplier())
  }
}

async function submitBid() {
  const row = bidRow.value
  if (!row?.id) return
  const items = supplierRows.value
    .map((r) => ({
      supplier_name: r.supplier_name.trim(),
      contact_person: r.contact_person.trim(),
      contact_phone: r.contact_phone.trim(),
      brand: r.brand.trim(),
      specification: r.specification.trim(),
      final_amount: r.final_amount.trim(),
      warranty_period: r.warranty_period.trim(),
      preferential_terms: r.preferential_terms.trim()
    }))
    .filter((r) => r.supplier_name)
  for (const it of items) {
    if (it.final_amount && Number.isNaN(Number(it.final_amount))) {
      ElMessage.warning('最终金额须为数字')
      return
    }
  }
  bidSubmitting.value = true
  try {
    await http.put(`/purchase/bidding/approved-items/${row.id}/suppliers`, { items })
    ElMessage.success('招标供应商已保存')
    bidVisible.value = false
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    bidSubmitting.value = false
  }
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

<style scoped>
.bid-split {
  display: grid;
  grid-template-columns: minmax(280px, 38%) minmax(420px, 1fr);
  gap: 16px;
  align-items: start;
}
.bid-left,
.bid-right {
  min-width: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 12px;
  background: var(--el-bg-color);
}
.bid-panel-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.bid-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.bid-panel-head .bid-panel-title {
  margin-bottom: 0;
}
.bid-supplier-table {
  width: 100%;
}
@media (max-width: 1100px) {
  .bid-split {
    grid-template-columns: 1fr;
  }
}
</style>
