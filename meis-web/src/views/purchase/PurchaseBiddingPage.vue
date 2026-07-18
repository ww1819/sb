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
          placeholder="单号 / 订单号 / 计划单号 / 设备名称 / 科室 / 规格型号"
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
        <el-table-column prop="bidding_no" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="device_name" label="设备名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dept_name" label="申请科室" min-width="140" show-overflow-tooltip />
        <el-table-column prop="order_no" label="订单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="plan_code" label="计划单号" min-width="140" show-overflow-tooltip />
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
          <div class="bid-panel-scroll">
            <el-table :data="bidLeftRows" border size="small" class="bid-left-table" style="width: 720px">
              <el-table-column type="index" label="序号" width="52" align="center" />
              <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="dept_name" label="申请科室" min-width="120" show-overflow-tooltip />
              <el-table-column prop="specification" label="规格型号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="quantity" label="数量" width="90" align="right">
                <template #default="{ row }">
                  <TableCellValue :field="qtyField" :value="row.quantity" />
                </template>
              </el-table-column>
              <el-table-column prop="total_price" label="总金额" width="120" align="right">
                <template #default="{ row }">
                  <TableCellValue :field="totalField" :value="row.total_price" />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
        <div class="bid-right">
          <div class="bid-panel-head">
            <div class="bid-panel-title">供应商信息</div>
            <el-button type="primary" link @click="addSupplierRow">新增行</el-button>
          </div>
          <div class="bid-panel-scroll">
            <el-table :data="supplierRows" border size="small" class="bid-supplier-table" style="width: 1380px">
              <el-table-column label="中标" width="64" align="center" fixed="left">
                <template #default="{ row }">
                  <el-radio
                    :model-value="winnerKey"
                    :value="row._key"
                    @change="() => setWinner(row._key)"
                  />
                </template>
              </el-table-column>
              <el-table-column type="index" label="序号" width="52" align="center" />
              <el-table-column label="供应商名称" min-width="180">
                <template #default="{ row }">
                  <el-select
                    v-model="row.supplier_id"
                    filterable
                    clearable
                    placeholder="请选择供应商"
                    style="width: 100%"
                    @change="(v) => onSupplierChange(row, v ? String(v) : null)"
                  >
                    <el-option
                      v-for="s in supplierOptions"
                      :key="s.id"
                      :label="s.label"
                      :value="s.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="联系人" width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.contact_person || '-' }}</template>
              </el-table-column>
              <el-table-column label="联系电话" width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.contact_phone || '-' }}</template>
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
              <el-table-column label="投标信息" width="140" align="center">
                <template #default="{ row }">
                  <div class="bid-file-cell">
                    <el-upload :show-file-list="false" :http-request="(opt) => onBidUpload(row, opt)">
                      <el-button link type="primary" :loading="row._uploading">上传</el-button>
                    </el-upload>
                    <el-button
                      v-if="row.bid_doc_url"
                      link
                      type="primary"
                      :loading="row._downloading"
                      @click="onBidDownload(row)"
                    >
                      下载
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center" fixed="right">
                <template #default="{ row, $index }">
                  <el-button link type="danger" @click="removeSupplierRow(row, $index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
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
import type { TableInstance, UploadRequestOptions } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import AppModal from '@/components/AppModal.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope } from '@/composables/useListActionScope'
import { downloadApiFile } from '@/utils/fileDownload'

type SupplierOption = {
  id: string
  label: string
  supplier_name: string
  contact_person: string
  contact_phone: string
}

type SupplierRow = {
  _key: string
  id?: string
  supplier_id: string
  supplier_name: string
  contact_person: string
  contact_phone: string
  brand: string
  specification: string
  final_amount: string
  warranty_period: string
  preferential_terms: string
  bid_doc_url: string
  is_winner: boolean
  _uploading?: boolean
  _downloading?: boolean
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
const supplierOptions = ref<SupplierOption[]>([])
const winnerKey = ref('')

const qtyField: FieldSchema = { prop: 'quantity', label: '数量', type: 'number' }
const totalField: FieldSchema = { prop: 'total_price', label: '总金额', type: 'number' }

const bidLeftRows = computed(() => (bidRow.value ? [bidRow.value] : []))

let supplierKeySeq = 0
function nextSupplierKey() {
  supplierKeySeq += 1
  return `s-${supplierKeySeq}`
}

function emptySupplier(): SupplierRow {
  return {
    _key: nextSupplierKey(),
    supplier_id: '',
    supplier_name: '',
    contact_person: '',
    contact_phone: '',
    brand: '',
    specification: '',
    final_amount: '',
    warranty_period: '',
    preferential_terms: '',
    bid_doc_url: '',
    is_winner: false
  }
}

function setWinner(key: string) {
  winnerKey.value = key
  for (const r of supplierRows.value) {
    r.is_winner = r._key === key
  }
}

function onSupplierChange(row: SupplierRow, supplierId: string | null) {
  const id = supplierId || ''
  row.supplier_id = id
  const found = supplierOptions.value.find((s) => s.id === id)
  if (found) {
    row.supplier_name = found.supplier_name
    row.contact_person = found.contact_person
    row.contact_phone = found.contact_phone
  } else {
    row.supplier_name = ''
    row.contact_person = ''
    row.contact_phone = ''
  }
}

async function loadSupplierOptions() {
  const { data } = await http.get('/system/supplier/list', { params: { limit: 500 } })
  const list = (data.data?.records ?? data.data ?? []) as Record<string, unknown>[]
  supplierOptions.value = list
    .filter((r) => r.id != null)
    .map((r) => {
      const name = String(r.supplier_name ?? '')
      const code = r.supplier_code != null ? String(r.supplier_code) : ''
      return {
        id: String(r.id),
        label: code ? `${code} ${name}` : name,
        supplier_name: name,
        contact_person: String(r.contact_person ?? ''),
        contact_phone: String(r.contact_phone ?? '')
      }
    })
}

async function onBidUpload(row: SupplierRow, options: UploadRequestOptions) {
  row._uploading = true
  try {
    const form = new FormData()
    form.append('file', options.file as File)
    const { data } = await http.post('/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code !== 0 || !data.data?.url) {
      ElMessage.error(data.message || '上传失败')
      return
    }
    row.bid_doc_url = String(data.data.url)
    ElMessage.success('上传成功')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    row._uploading = false
  }
}

async function onBidDownload(row: SupplierRow) {
  if (!row.bid_doc_url) return
  row._downloading = true
  try {
    const name = row.supplier_name ? `${row.supplier_name}_投标信息` : '投标信息'
    await downloadApiFile(row.bid_doc_url, name)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '下载失败'
    ElMessage.error(msg || '下载失败')
  } finally {
    row._downloading = false
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
  winnerKey.value = ''
  try {
    await loadSupplierOptions()
    const { data } = await http.get(`/purchase/bidding/approved-items/${row.id}/suppliers`)
    const list = (data.data ?? []) as Record<string, unknown>[]
    if (list.length) {
      supplierRows.value = list.map((r) => {
        const key = nextSupplierKey()
        const winner = r.is_winner === true || r.is_winner === 'true' || r.is_winner === 1
        if (winner) winnerKey.value = key
        return {
          _key: key,
          id: r.id != null ? String(r.id) : undefined,
          supplier_id: r.supplier_id != null ? String(r.supplier_id) : '',
          supplier_name: String(r.supplier_name ?? ''),
          contact_person: String(r.contact_person ?? ''),
          contact_phone: String(r.contact_phone ?? ''),
          brand: String(r.brand ?? ''),
          specification: String(r.specification ?? ''),
          final_amount: r.final_amount == null ? '' : String(r.final_amount),
          warranty_period: String(r.warranty_period ?? ''),
          preferential_terms: String(r.preferential_terms ?? ''),
          bid_doc_url: String(r.bid_doc_url ?? ''),
          is_winner: winner
        }
      })
    } else {
      supplierRows.value = [emptySupplier()]
    }
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '加载供应商明细失败')
    bidVisible.value = false
  }
}

function addSupplierRow() {
  supplierRows.value.push(emptySupplier())
}

function removeSupplierRow(row: SupplierRow, index: number) {
  supplierRows.value.splice(index, 1)
  if (winnerKey.value === row._key) winnerKey.value = ''
  if (!supplierRows.value.length) {
    supplierRows.value.push(emptySupplier())
  }
}

async function submitBid() {
  const row = bidRow.value
  if (!row?.id) return
  const items = supplierRows.value
    .map((r) => ({
      supplier_id: r.supplier_id || null,
      supplier_name: r.supplier_name.trim(),
      contact_person: r.contact_person.trim(),
      contact_phone: r.contact_phone.trim(),
      brand: r.brand.trim(),
      specification: r.specification.trim(),
      final_amount: r.final_amount.trim(),
      warranty_period: r.warranty_period.trim(),
      preferential_terms: r.preferential_terms.trim(),
      bid_doc_url: r.bid_doc_url.trim(),
      is_winner: r._key === winnerKey.value
    }))
    .filter((r) => r.supplier_id || r.supplier_name)
  if (items.some((r) => !r.supplier_id)) {
    ElMessage.warning('请选择供应商')
    return
  }
  for (const it of items) {
    if (it.final_amount && Number.isNaN(Number(it.final_amount))) {
      ElMessage.warning('最终金额须为数字')
      return
    }
  }
  const winners = items.filter((r) => r.is_winner)
  if (winners.length > 1) {
    ElMessage.warning('同一明细只能选择一个中标供应商')
    return
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
    const headers = ['单号', '设备名称', '申请科室', '订单号', '计划单号', '规格型号', '数量', '总金额']
    const lines = [headers.join(',')]
    for (const r of list) {
      const cells = [
        r.bidding_no,
        r.device_name,
        r.dept_name,
        r.order_no,
        r.plan_code,
        r.specification,
        r.quantity,
        r.total_price
      ].map((v) => {
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
  align-items: stretch;
  min-height: 480px;
}
.bid-left,
.bid-right {
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 480px;
  max-height: 62vh;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 12px;
  background: var(--el-bg-color);
}
.bid-panel-title {
  font-weight: 600;
  margin-bottom: 8px;
  flex-shrink: 0;
}
.bid-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  flex-shrink: 0;
}
.bid-panel-head .bid-panel-title {
  margin-bottom: 0;
}
.bid-panel-scroll {
  flex: 1;
  min-height: 0;
  overflow-x: scroll;
  overflow-y: auto;
  /* 始终预留滚动条槽位，避免悬停才出现 */
  scrollbar-gutter: stable both-edges;
}
.bid-panel-scroll::-webkit-scrollbar {
  height: 12px;
  width: 12px;
}
.bid-panel-scroll::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 6px;
  border: 2px solid #f0f2f5;
}
.bid-panel-scroll::-webkit-scrollbar-track {
  background: #f0f2f5;
  border-radius: 6px;
}
.bid-panel-scroll {
  scrollbar-width: thin;
  scrollbar-color: #c0c4cc #f0f2f5;
}
.bid-left-table,
.bid-supplier-table {
  width: max-content;
  min-width: 100%;
}
.bid-file-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}
@media (max-width: 1100px) {
  .bid-split {
    grid-template-columns: 1fr;
  }
  .bid-left,
  .bid-right {
    height: 360px;
  }
}
</style>
