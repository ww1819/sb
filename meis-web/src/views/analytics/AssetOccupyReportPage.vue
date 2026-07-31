<template>
  <div class="asset-occupy-report">
    <el-tabs v-model="activeTab" class="report-tabs">
      <el-tab-pane label="明细表" name="detail" />
      <el-tab-pane label="汇总表" name="summary" />
    </el-tabs>

    <template v-if="activeTab === 'detail'">
      <div class="query-box">
        <div class="section-bar">查询条件</div>
        <div class="query-body">
          <el-form :model="detailFilters" class="filter-form" label-width="100px" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="价格区间">
                  <el-input
                    v-model="detailFilters.priceRange"
                    clearable
                    placeholder="价格区间搜索"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="科室">
                  <el-input v-model="detailFilters.dept" clearable placeholder="科室搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
                <el-form-item label-width="0">
                  <el-button type="primary" @click="onDetailSearch">查询</el-button>
                  <el-button @click="onDetailReset">重置</el-button>
                  <el-button @click="onDetailExport">导出</el-button>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="资产名称">
                  <el-input
                    v-model="detailFilters.assetName"
                    clearable
                    placeholder="资产名称搜索"
                    style="max-width: 320px"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">资产在用统计-明细表</div>
        <el-table
          :data="pagedDetailRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onDetailSortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="detailIndexMethod" />
          <el-table-column prop="price_range" label="价格区间" min-width="120" sortable="custom" />
          <el-table-column prop="dept" label="科室" min-width="120" show-overflow-tooltip sortable="custom" />
          <el-table-column prop="asset_code" label="资产编码" min-width="130" sortable="custom" />
          <el-table-column
            prop="asset_name"
            label="资产名称"
            min-width="140"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column prop="specification" label="规格/型号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="90" align="right" sortable="custom" />
          <el-table-column prop="unit_price" label="单价" min-width="120" align="right" sortable="custom" />
          <el-table-column prop="amount" label="金额(元)" min-width="130" align="right" sortable="custom" />
          <el-table-column prop="manufacturer" label="生产厂家" min-width="140" show-overflow-tooltip />
          <el-table-column prop="supplier" label="供应商" min-width="140" show-overflow-tooltip />
          <el-table-column prop="serial_no" label="序列号" min-width="130" show-overflow-tooltip />
        </el-table>
        <div class="table-footer">
          <div class="table-footer-summary">
            <span>合计：</span>
            <span>总数量：{{ detailSummary.totalQty }}</span>
            <span>总金额：{{ detailSummary.totalAmount }}</span>
            <span>当前页面数量：{{ detailSummary.pageQty }}</span>
            <span>当前页面金额：{{ detailSummary.pageAmount }}</span>
          </div>
          <div class="table-footer-pager">
            <el-pagination
              v-model:current-page="detailPage"
              v-model:page-size="detailPageSize"
              :page-sizes="[10, 20, 30, 50]"
              :total="filteredDetailRows.length"
              layout="total, sizes, prev, pager, next, jumper"
              background
            />
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="query-box">
        <div class="section-bar">查询条件</div>
        <div class="query-body">
          <el-form :model="summaryFilters" class="filter-form" label-width="100px" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="科室">
                  <el-input v-model="summaryFilters.dept" clearable placeholder="科室搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
                <el-form-item label-width="0">
                  <el-button type="primary" @click="onSummarySearch">查询</el-button>
                  <el-button @click="onSummaryReset">重置</el-button>
                  <el-button @click="onSummaryExport">导出</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">资产在用统计-汇总表</div>
        <el-table
          :data="pagedSummaryRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onSummarySortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="summaryIndexMethod" />
          <el-table-column prop="dept" label="科室" min-width="160" show-overflow-tooltip sortable="custom" />
          <el-table-column prop="quantity" label="数量" min-width="120" align="right" sortable="custom" />
          <el-table-column prop="amount" label="金额(元)" min-width="160" align="right" sortable="custom" />
        </el-table>
        <div class="table-footer">
          <div class="table-footer-summary">
            <span>合计：</span>
            <span>总数量：{{ summarySummary.totalQty }}</span>
            <span>总金额：{{ summarySummary.totalAmount }}</span>
            <span>当前页面数量：{{ summarySummary.pageQty }}</span>
            <span>当前页面金额：{{ summarySummary.pageAmount }}</span>
          </div>
          <div class="table-footer-pager">
            <el-pagination
              v-model:current-page="summaryPage"
              v-model:page-size="summaryPageSize"
              :page-sizes="[10, 20, 30, 50]"
              :total="filteredSummaryRows.length"
              layout="total, sizes, prev, pager, next, jumper"
              background
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadExcelHtml, escapeHtml, formatExportDate } from '@/utils/excelHtmlExport'

const activeTab = ref<'detail' | 'summary'>('detail')

const detailFilters = reactive({
  priceRange: '',
  dept: '',
  assetName: ''
})
const summaryFilters = reactive({
  dept: ''
})

const PRICE_RANGES = ['万元以下', '1-10万', '10-20万', '20-50万', '50万元以上'] as const
const MOCK_DEPTS = [
  '放射科',
  '超声科',
  '检验科',
  '手术室',
  'ICU',
  '急诊科',
  '心内科',
  '骨科',
  '口腔科',
  '供应室'
]
const MOCK_NAMES = [
  '多参数监护仪',
  '输液泵',
  '呼吸机',
  '超声诊断仪',
  '心电图机',
  '除颤仪',
  '麻醉机',
  '血球仪',
  '生化分析仪',
  '牙科综合治疗台'
]
const MOCK_SPECS = ['A1', 'B2-Pro', 'Portable X', 'S300', 'V6', 'Lite']
const MOCK_MAKERS = ['迈瑞', '飞利浦', 'GE医疗', '西门子', '鱼跃', '理邦']
const MOCK_SUPPLIERS = ['华康器械', '安泰医疗', '博远供应', '仁和贸易', '康达商贸']

type DetailRow = {
  price_range: string
  dept: string
  asset_code: string
  asset_name: string
  specification: string
  quantity: string
  unit_price: string
  amount: string
  manufacturer: string
  supplier: string
  serial_no: string
  _qty: number
  _unit: number
  _amount: number
}

type SummaryRow = {
  dept: string
  quantity: string
  amount: string
  _qty: number
  _amount: number
}

function formatMoney(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function pickPriceRange(unitPrice: number) {
  if (unitPrice < 10000) return PRICE_RANGES[0]
  if (unitPrice < 100000) return PRICE_RANGES[1]
  if (unitPrice < 200000) return PRICE_RANGES[2]
  if (unitPrice < 500000) return PRICE_RANGES[3]
  return PRICE_RANGES[4]
}

/** 前端样式示意：50 条测试数据，不请求后台 */
function buildMockDetailRows(count = 50): DetailRow[] {
  const rows: DetailRow[] = []
  for (let i = 1; i <= count; i++) {
    const unit = 4200 + ((i * 18341) % 720000)
    const qty = 1 + ((i * 3) % 5)
    const amount = unit * qty
    rows.push({
      price_range: pickPriceRange(unit),
      dept: MOCK_DEPTS[(i - 1) % MOCK_DEPTS.length],
      asset_code: `ZY-${String(i).padStart(4, '0')}`,
      asset_name: MOCK_NAMES[(i - 1) % MOCK_NAMES.length],
      specification: MOCK_SPECS[(i - 1) % MOCK_SPECS.length],
      quantity: String(qty),
      unit_price: formatMoney(unit),
      amount: formatMoney(amount),
      manufacturer: MOCK_MAKERS[(i - 1) % MOCK_MAKERS.length],
      supplier: MOCK_SUPPLIERS[(i - 1) % MOCK_SUPPLIERS.length],
      serial_no: `SN${20241000 + i}`,
      _qty: qty,
      _unit: unit,
      _amount: amount
    })
  }
  return rows
}

function buildMockSummaryRows(details: DetailRow[]): SummaryRow[] {
  const map = new Map<string, { qty: number; amount: number }>()
  for (const row of details) {
    const cur = map.get(row.dept) ?? { qty: 0, amount: 0 }
    cur.qty += row._qty
    cur.amount += row._amount
    map.set(row.dept, cur)
  }
  return [...map.entries()].map(([dept, v]) => ({
    dept,
    quantity: String(v.qty),
    amount: formatMoney(v.amount),
    _qty: v.qty,
    _amount: v.amount
  }))
}

const allDetailRows = ref<DetailRow[]>(buildMockDetailRows(50))
const allSummaryRows = ref<SummaryRow[]>(buildMockSummaryRows(allDetailRows.value))

const detailPage = ref(1)
const detailPageSize = ref(10)
const detailSort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null
})

const summaryPage = ref(1)
const summaryPageSize = ref(10)
const summarySort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null
})

function matchDetail(row: DetailRow) {
  const rangeKw = detailFilters.priceRange.trim()
  const deptKw = detailFilters.dept.trim()
  const nameKw = detailFilters.assetName.trim()
  if (rangeKw && !row.price_range.includes(rangeKw)) return false
  if (deptKw && !row.dept.includes(deptKw)) return false
  if (nameKw && !row.asset_name.includes(nameKw)) return false
  return true
}

const filteredDetailRows = computed(() => {
  let rows = allDetailRows.value.filter(matchDetail)
  const { prop, order } = detailSort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  rows = [...rows]
  rows.sort((a, b) => {
    if (prop === 'quantity') return (a._qty - b._qty) * factor
    if (prop === 'unit_price') return (a._unit - b._unit) * factor
    if (prop === 'amount') return (a._amount - b._amount) * factor
    const av = String((a as Record<string, unknown>)[prop] ?? '')
    const bv = String((b as Record<string, unknown>)[prop] ?? '')
    return av.localeCompare(bv, 'zh-CN') * factor
  })
  return rows
})

const pagedDetailRows = computed(() => {
  const start = (detailPage.value - 1) * detailPageSize.value
  return filteredDetailRows.value.slice(start, start + detailPageSize.value)
})

const detailSummary = computed(() => {
  const rows = filteredDetailRows.value
  const page = pagedDetailRows.value
  return {
    totalQty: rows.reduce((s, r) => s + r._qty, 0),
    totalAmount: formatMoney(rows.reduce((s, r) => s + r._amount, 0)),
    pageQty: page.reduce((s, r) => s + r._qty, 0),
    pageAmount: formatMoney(page.reduce((s, r) => s + r._amount, 0))
  }
})

const filteredSummaryRows = computed(() => {
  const kw = summaryFilters.dept.trim()
  let rows = allSummaryRows.value
  if (kw) rows = rows.filter((r) => r.dept.includes(kw))
  const { prop, order } = summarySort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  rows = [...rows]
  rows.sort((a, b) => {
    if (prop === 'quantity') return (a._qty - b._qty) * factor
    if (prop === 'amount') return (a._amount - b._amount) * factor
    return a.dept.localeCompare(b.dept, 'zh-CN') * factor
  })
  return rows
})

const pagedSummaryRows = computed(() => {
  const start = (summaryPage.value - 1) * summaryPageSize.value
  return filteredSummaryRows.value.slice(start, start + summaryPageSize.value)
})

const summarySummary = computed(() => {
  const rows = filteredSummaryRows.value
  const page = pagedSummaryRows.value
  return {
    totalQty: rows.reduce((s, r) => s + r._qty, 0),
    totalAmount: formatMoney(rows.reduce((s, r) => s + r._amount, 0)),
    pageQty: page.reduce((s, r) => s + r._qty, 0),
    pageAmount: formatMoney(page.reduce((s, r) => s + r._amount, 0))
  }
})

function detailIndexMethod(index: number) {
  return (detailPage.value - 1) * detailPageSize.value + index + 1
}
function summaryIndexMethod(index: number) {
  return (summaryPage.value - 1) * summaryPageSize.value + index + 1
}

function onDetailSortChange(payload: { prop: string; order: 'ascending' | 'descending' | null }) {
  detailSort.value = { prop: payload.prop || '', order: payload.order }
  detailPage.value = 1
}
function onSummarySortChange(payload: { prop: string; order: 'ascending' | 'descending' | null }) {
  summarySort.value = { prop: payload.prop || '', order: payload.order }
  summaryPage.value = 1
}

function onDetailSearch() {
  detailPage.value = 1
}
function onDetailReset() {
  detailFilters.priceRange = ''
  detailFilters.dept = ''
  detailFilters.assetName = ''
  detailPage.value = 1
}
function onSummarySearch() {
  summaryPage.value = 1
}
function onSummaryReset() {
  summaryFilters.dept = ''
  summaryPage.value = 1
}

function onDetailExport() {
  const rows = filteredDetailRows.value
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  let totalQty = 0
  let totalAmount = 0
  const body = rows
    .map((r, i) => {
      totalQty += r._qty
      totalAmount += r._amount
      return `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.price_range)}</td>
      <td>${escapeHtml(r.dept)}</td>
      <td>${escapeHtml(r.asset_code)}</td>
      <td>${escapeHtml(r.asset_name)}</td>
      <td>${escapeHtml(r.specification)}</td>
      <td>${escapeHtml(r.quantity)}</td>
      <td>${escapeHtml(r.unit_price)}</td>
      <td>${escapeHtml(r.amount)}</td>
      <td>${escapeHtml(r.manufacturer)}</td>
      <td>${escapeHtml(r.supplier)}</td>
      <td>${escapeHtml(r.serial_no)}</td>
    </tr>`
    })
    .join('')
  const totalRow = `<tr>
      <td></td><td></td><td></td><td></td>
      <td><b>合计</b></td>
      <td></td>
      <td><b>${totalQty}</b></td>
      <td></td>
      <td><b>${escapeHtml(formatMoney(totalAmount))}</b></td>
      <td></td><td></td><td></td>
    </tr>`
  const table = `<table border="1">
    <thead>
      <tr>
        <th>序号</th><th>价格区间</th><th>科室</th><th>资产编码</th><th>资产名称</th><th>规格/型号</th>
        <th>数量</th><th>单价</th><th>金额(元)</th><th>生产厂家</th><th>供应商</th><th>序列号</th>
      </tr>
    </thead>
    <tbody>${body}${totalRow}</tbody>
  </table>`
  downloadExcelHtml(table, `资产在用统计-明细表${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

function onSummaryExport() {
  const rows = filteredSummaryRows.value
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  let totalQty = 0
  let totalAmount = 0
  const body = rows
    .map((r, i) => {
      totalQty += r._qty
      totalAmount += r._amount
      return `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.dept)}</td>
      <td>${escapeHtml(r.quantity)}</td>
      <td>${escapeHtml(r.amount)}</td>
    </tr>`
    })
    .join('')
  const totalRow = `<tr>
      <td></td>
      <td><b>合计</b></td>
      <td><b>${totalQty}</b></td>
      <td><b>${escapeHtml(formatMoney(totalAmount))}</b></td>
    </tr>`
  const table = `<table border="1">
    <thead>
      <tr>
        <th>序号</th><th>科室</th><th>数量</th><th>金额(元)</th>
      </tr>
    </thead>
    <tbody>${body}${totalRow}</tbody>
  </table>`
  downloadExcelHtml(table, `资产在用统计-汇总表${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}
</script>

<style scoped>
.asset-occupy-report {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding-bottom: 16px;
  box-sizing: border-box;
}

.report-tabs {
  margin-bottom: 0;
  flex-shrink: 0;
}

.report-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.query-box {
  margin-bottom: 8px;
  margin-top: 0;
  background: #fff;
  border: 1px solid var(--meis-report-border);
  border-radius: 2px;
  overflow: hidden;
  flex-shrink: 0;
}

.query-body {
  padding: 12px 16px 4px;
}

.section-bar {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--el-text-color-primary, #303133);
  background: transparent;
  border-bottom: 1px solid var(--meis-report-line);
}

.query-box > .section-bar {
  background: var(--meis-report-header-bg);
  border-bottom: 1px solid var(--meis-report-border);
}

.filter-form {
  margin-bottom: 0;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 12px;
  width: 100%;
}

.filter-actions :deep(.el-form-item__content) {
  justify-content: flex-start;
}

.table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--meis-report-line);
  border-radius: 2px;
  overflow: hidden;
  background: #fff;
}

.table-wrap .section-bar {
  flex-shrink: 0;
}

.table-wrap :deep(.detail-table) {
  flex: 1;
}

.table-wrap :deep(.el-table) {
  border: none;
  --el-table-border-color: var(--meis-report-line);
  --el-table-header-bg-color: var(--meis-report-header-bg);
  --el-table-tr-bg-color: #ffffff;
  --el-table-row-hover-bg-color: var(--meis-report-hover);
}

.table-wrap :deep(.el-table::before),
.table-wrap :deep(.el-table--border::after) {
  display: none;
}

.table-wrap :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.table-wrap :deep(.el-table th.el-table__cell) {
  background-color: var(--meis-report-header-bg) !important;
  color: #303133 !important;
  font-weight: 400;
}

.table-wrap :deep(.el-table th.el-table__cell .cell) {
  color: #303133 !important;
  font-weight: 400;
  white-space: nowrap;
}

.table-wrap :deep(.el-table td.el-table__cell) {
  background-color: #ffffff;
  color: #303133;
  padding: 10px 0;
}

.table-wrap :deep(.el-table__row--striped td.el-table__cell) {
  background-color: var(--meis-report-stripe);
}

.table-wrap :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: var(--meis-report-hover) !important;
}

.table-wrap :deep(.el-table--border .el-table__cell) {
  border-right-color: var(--meis-report-line);
}

.table-wrap :deep(.el-table td.el-table__cell),
.table-wrap :deep(.el-table th.el-table__cell.is-leaf) {
  border-bottom-color: var(--meis-report-line);
}

.table-wrap :deep(.el-table--border::before),
.table-wrap :deep(.el-table__border-left-patch) {
  background-color: var(--meis-report-line);
}

.table-footer {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: var(--meis-report-header-bg);
  border-top: 1px solid var(--meis-report-line);
}

.table-footer-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #303133;
}

.table-footer-summary > span:first-child {
  font-weight: 600;
}

.table-footer-pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
</style>
