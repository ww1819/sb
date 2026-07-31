<template>
  <div class="asset-transfer-report">
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
                <el-form-item label="转科单号">
                  <el-input
                    v-model="detailFilters.transferNo"
                    clearable
                    placeholder="转科单号搜索"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="资产名称">
                  <el-input
                    v-model="detailFilters.assetName"
                    clearable
                    placeholder="资产名称搜索"
                  />
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
                <el-form-item label="调出科室">
                  <el-input
                    v-model="detailFilters.fromDept"
                    clearable
                    placeholder="调出科室搜索"
                    style="max-width: 320px"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">资产移动统计表-明细表</div>
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
          <el-table-column prop="transfer_no" label="转科单号" min-width="130" sortable="custom" />
          <el-table-column prop="apply_date" label="申请日期" min-width="110" align="center" sortable="custom" />
          <el-table-column prop="transfer_date" label="转科日期" min-width="110" align="center" sortable="custom" />
          <el-table-column prop="asset_code" label="资产编号" min-width="120" sortable="custom" />
          <el-table-column prop="serial_no" label="资产序列号" min-width="130" show-overflow-tooltip />
          <el-table-column
            prop="asset_name"
            label="资产名称"
            min-width="140"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column prop="barcode" label="条码号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="brand" label="资产品牌" min-width="110" show-overflow-tooltip />
          <el-table-column prop="model" label="型号" min-width="110" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="original_value" label="原值（元）" min-width="120" align="right" sortable="custom" />
          <el-table-column prop="residual_value" label="残值（元）" min-width="120" align="right" />
          <el-table-column prop="from_dept" label="调出科室" min-width="110" show-overflow-tooltip sortable="custom" />
          <el-table-column prop="from_owner" label="原负责人" min-width="100" show-overflow-tooltip />
          <el-table-column prop="to_dept" label="调入科室" min-width="110" show-overflow-tooltip />
          <el-table-column prop="to_owner" label="现负责人" min-width="100" show-overflow-tooltip />
          <el-table-column prop="reason" label="转科原因" min-width="120" show-overflow-tooltip />
          <el-table-column prop="doc_no" label="转科文号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="years" label="年限" width="80" align="right" />
          <el-table-column prop="depr_status" label="折旧状态" min-width="100" align="center" />
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
        <div class="section-bar">资产移动统计表-汇总表</div>
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
  transferNo: '',
  assetName: '',
  fromDept: ''
})
const summaryFilters = reactive({
  dept: ''
})

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
const MOCK_BRANDS = ['迈瑞', '飞利浦', 'GE', '西门子', '鱼跃', '理邦']
const MOCK_MODELS = ['A1', 'B2-Pro', 'S300', 'V6', 'Lite', 'X5']
const MOCK_OWNERS = ['张伟', '李娜', '王强', '赵敏', '陈浩', '刘芳']
const MOCK_REASONS = ['科室调整', '设备调配', '业务需要', '资源整合', '临时借用']
const MOCK_DEPR = ['在用折旧', '提足折旧', '未折旧', '停用']

type DetailRow = {
  transfer_no: string
  apply_date: string
  transfer_date: string
  asset_code: string
  serial_no: string
  asset_name: string
  barcode: string
  brand: string
  model: string
  unit: string
  original_value: string
  residual_value: string
  from_dept: string
  from_owner: string
  to_dept: string
  to_owner: string
  reason: string
  doc_no: string
  years: string
  depr_status: string
  _qty: number
  _original: number
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

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function mockDate(i: number, dayOffset: number) {
  const month = 1 + ((i + dayOffset) % 12)
  const day = 1 + ((i * 3 + dayOffset) % 28)
  return `2026-${pad2(month)}-${pad2(day)}`
}

/** 前端样式示意：50 条测试数据，不请求后台 */
function buildMockDetailRows(count = 50): DetailRow[] {
  const rows: DetailRow[] = []
  for (let i = 1; i <= count; i++) {
    const original = 18000 + ((i * 19381) % 860000)
    const residual = Math.round(original * (0.05 + ((i * 3) % 10) / 100))
    const fromIdx = (i - 1) % MOCK_DEPTS.length
    const toIdx = (i + 3) % MOCK_DEPTS.length
    rows.push({
      transfer_no: `ZK${20260000 + i}`,
      apply_date: mockDate(i, 0),
      transfer_date: mockDate(i, 5),
      asset_code: `ZC-${String(i).padStart(4, '0')}`,
      serial_no: `SN${20242000 + i}`,
      asset_name: MOCK_NAMES[(i - 1) % MOCK_NAMES.length],
      barcode: `BC${88000000 + i}`,
      brand: MOCK_BRANDS[(i - 1) % MOCK_BRANDS.length],
      model: MOCK_MODELS[(i - 1) % MOCK_MODELS.length],
      unit: '台',
      original_value: formatMoney(original),
      residual_value: formatMoney(residual),
      from_dept: MOCK_DEPTS[fromIdx],
      from_owner: MOCK_OWNERS[(i - 1) % MOCK_OWNERS.length],
      to_dept: MOCK_DEPTS[toIdx],
      to_owner: MOCK_OWNERS[(i + 2) % MOCK_OWNERS.length],
      reason: MOCK_REASONS[(i - 1) % MOCK_REASONS.length],
      doc_no: `文号〔2026〕${pad2(i)}号`,
      years: String(3 + (i % 8)),
      depr_status: MOCK_DEPR[(i - 1) % MOCK_DEPR.length],
      _qty: 1,
      _original: original
    })
  }
  return rows
}

/** 汇总按调出科室归集（示意） */
function buildMockSummaryRows(details: DetailRow[]): SummaryRow[] {
  const map = new Map<string, { qty: number; amount: number }>()
  for (const row of details) {
    const cur = map.get(row.from_dept) ?? { qty: 0, amount: 0 }
    cur.qty += row._qty
    cur.amount += row._original
    map.set(row.from_dept, cur)
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
  const noKw = detailFilters.transferNo.trim()
  const nameKw = detailFilters.assetName.trim()
  const deptKw = detailFilters.fromDept.trim()
  if (noKw && !row.transfer_no.includes(noKw)) return false
  if (nameKw && !row.asset_name.includes(nameKw)) return false
  if (deptKw && !row.from_dept.includes(deptKw)) return false
  return true
}

const filteredDetailRows = computed(() => {
  let rows = allDetailRows.value.filter(matchDetail)
  const { prop, order } = detailSort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  rows = [...rows]
  rows.sort((a, b) => {
    if (prop === 'original_value') return (a._original - b._original) * factor
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
    totalAmount: formatMoney(rows.reduce((s, r) => s + r._original, 0)),
    pageQty: page.reduce((s, r) => s + r._qty, 0),
    pageAmount: formatMoney(page.reduce((s, r) => s + r._original, 0))
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
  detailFilters.transferNo = ''
  detailFilters.assetName = ''
  detailFilters.fromDept = ''
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
      totalAmount += r._original
      return `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.transfer_no)}</td>
      <td>${escapeHtml(r.apply_date)}</td>
      <td>${escapeHtml(r.transfer_date)}</td>
      <td>${escapeHtml(r.asset_code)}</td>
      <td>${escapeHtml(r.serial_no)}</td>
      <td>${escapeHtml(r.asset_name)}</td>
      <td>${escapeHtml(r.barcode)}</td>
      <td>${escapeHtml(r.brand)}</td>
      <td>${escapeHtml(r.model)}</td>
      <td>${escapeHtml(r.unit)}</td>
      <td>${escapeHtml(r.original_value)}</td>
      <td>${escapeHtml(r.residual_value)}</td>
      <td>${escapeHtml(r.from_dept)}</td>
      <td>${escapeHtml(r.from_owner)}</td>
      <td>${escapeHtml(r.to_dept)}</td>
      <td>${escapeHtml(r.to_owner)}</td>
      <td>${escapeHtml(r.reason)}</td>
      <td>${escapeHtml(r.doc_no)}</td>
      <td>${escapeHtml(r.years)}</td>
      <td>${escapeHtml(r.depr_status)}</td>
    </tr>`
    })
    .join('')
  const totalRow = `<tr>
      <td></td><td></td><td></td><td></td><td></td><td></td>
      <td><b>合计</b></td>
      <td></td><td></td><td></td><td></td>
      <td><b>${escapeHtml(formatMoney(totalAmount))}</b></td>
      <td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>
    </tr>`
  const table = `<table border="1">
    <thead>
      <tr>
        <th>序号</th><th>转科单号</th><th>申请日期</th><th>转科日期</th><th>资产编号</th>
        <th>资产序列号</th><th>资产名称</th><th>条码号</th><th>资产品牌</th><th>型号</th><th>单位</th>
        <th>原值（元）</th><th>残值（元）</th><th>调出科室</th><th>原负责人</th>
        <th>调入科室</th><th>现负责人</th><th>转科原因</th><th>转科文号</th><th>年限</th><th>折旧状态</th>
      </tr>
    </thead>
    <tbody>${body}${totalRow}</tbody>
  </table>`
  downloadExcelHtml(table, `资产移动统计表-明细表${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条（合计数量 ${totalQty}）`)
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
  downloadExcelHtml(table, `资产移动统计表-汇总表${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}
</script>

<style scoped>
.asset-transfer-report {
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
