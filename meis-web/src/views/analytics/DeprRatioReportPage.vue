<template>
  <div class="depr-ratio-report">
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
                <el-form-item label="设备编号">
                  <el-input v-model="detailFilters.deviceCode" clearable placeholder="设备编号搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="资产名称">
                  <el-input v-model="detailFilters.assetName" clearable placeholder="资产名称搜索" />
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
                <el-form-item label="使用科室">
                  <el-input
                    v-model="detailFilters.useDept"
                    clearable
                    placeholder="使用科室搜索"
                    style="max-width: 320px"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">折旧明细比例-明细表</div>
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
          <el-table-column prop="device_code" label="设备编号" min-width="120" sortable="custom" />
          <el-table-column prop="asset_name" label="资产名称" min-width="140" show-overflow-tooltip sortable="custom" />
          <el-table-column prop="specification" label="规格/型号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="use_dept" label="使用科室" min-width="110" show-overflow-tooltip sortable="custom" />
          <el-table-column prop="monthly_rate" label="月折旧率(%)" width="120" align="right" sortable="custom" />
          <el-table-column prop="monthly_amount" label="月折旧金额（元）" min-width="140" align="right" sortable="custom" />
          <el-table-column prop="original_value" label="原值（元）" min-width="120" align="right" sortable="custom" />
          <el-table-column prop="accum_depr" label="累计折旧金额（元）" min-width="150" align="right" />
          <el-table-column prop="net_value" label="净值（元）" min-width="120" align="right" />
          <el-table-column prop="purchase_date" label="购入日期" width="120" align="center" />
          <el-table-column prop="depr_dept" label="折旧科室" min-width="110" show-overflow-tooltip />
          <el-table-column prop="depr_ratio" label="折旧比例" width="100" align="right" />
          <el-table-column prop="depr_months" label="已折旧月份" width="110" align="right" />
          <el-table-column prop="storage_place" label="存放地点" min-width="120" show-overflow-tooltip />
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
                <el-form-item label="使用科室">
                  <el-input v-model="summaryFilters.useDept" clearable placeholder="使用科室搜索" />
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
        <div class="section-bar">折旧明细比例-汇总表</div>
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
          <el-table-column prop="use_dept" label="使用科室" min-width="140" sortable="custom" />
          <el-table-column prop="asset_count" label="资产数量" width="110" align="right" sortable="custom" />
          <el-table-column prop="original_value" label="原值合计（元）" min-width="140" align="right" sortable="custom" />
          <el-table-column prop="monthly_amount" label="月折旧合计（元）" min-width="150" align="right" sortable="custom" />
          <el-table-column prop="accum_depr" label="累计折旧合计（元）" min-width="160" align="right" />
          <el-table-column prop="net_value" label="净值合计（元）" min-width="140" align="right" />
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
  deviceCode: '',
  assetName: '',
  useDept: ''
})
const summaryFilters = reactive({
  useDept: ''
})

type DetailRow = {
  device_code: string
  asset_name: string
  specification: string
  unit: string
  use_dept: string
  monthly_rate: string
  monthly_amount: string
  original_value: string
  accum_depr: string
  net_value: string
  purchase_date: string
  depr_dept: string
  depr_ratio: string
  depr_months: string
  storage_place: string
  _monthly: number
  _original: number
  _accum: number
  _net: number
  _rate: number
}

type SummaryRow = {
  use_dept: string
  asset_count: string
  original_value: string
  monthly_amount: string
  accum_depr: string
  net_value: string
  _count: number
  _original: number
  _monthly: number
  _accum: number
  _net: number
}

const MOCK_ASSETS = ['CT机', 'MRI', '超声诊断仪', '呼吸机', '监护仪', '输液泵', '麻醉机', '血液透析机']
const MOCK_SPECS = ['标准型', '加强型', '便携式 A', '台式 B', '壁挂式']
const MOCK_DEPTS = ['放射科', '检验科', '手术室', 'ICU', '急诊科', '心内科', '骨科', '儿科']
const MOCK_PLACES = ['影像中心一楼', '检验楼二楼', '手术室东侧', 'ICU 病区', '急诊大厅', '库房 A']

function formatMoney(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function buildMockDetailRows(count = 50): DetailRow[] {
  const rows: DetailRow[] = []
  for (let i = 1; i <= count; i++) {
    const original = 60000 + ((i * 17359) % 920000)
    const rate = 0.4 + ((i * 3) % 20) / 10
    const monthly = Math.round((original * rate) / 100)
    const months = 6 + ((i * 5) % 48)
    const accum = Math.min(original, monthly * months)
    const net = original - accum
    const ratio = original ? ((accum / original) * 100).toFixed(2) : '0.00'
    const y = 2018 + (i % 7)
    const m = 1 + (i % 12)
    const d = 1 + (i % 27)
    const dept = MOCK_DEPTS[(i - 1) % MOCK_DEPTS.length]
    rows.push({
      device_code: `SB${String(i).padStart(5, '0')}`,
      asset_name: MOCK_ASSETS[(i - 1) % MOCK_ASSETS.length],
      specification: MOCK_SPECS[(i - 1) % MOCK_SPECS.length],
      unit: '台',
      use_dept: dept,
      monthly_rate: rate.toFixed(2),
      monthly_amount: formatMoney(monthly),
      original_value: formatMoney(original),
      accum_depr: formatMoney(accum),
      net_value: formatMoney(net),
      purchase_date: `${y}-${pad2(m)}-${pad2(d)}`,
      depr_dept: dept,
      depr_ratio: `${ratio}%`,
      depr_months: String(months),
      storage_place: MOCK_PLACES[(i - 1) % MOCK_PLACES.length],
      _monthly: monthly,
      _original: original,
      _accum: accum,
      _net: net,
      _rate: rate
    })
  }
  return rows
}

function buildMockSummaryRows(details: DetailRow[]): SummaryRow[] {
  const map = new Map<
    string,
    { count: number; original: number; monthly: number; accum: number; net: number }
  >()
  for (const row of details) {
    const cur = map.get(row.use_dept) ?? { count: 0, original: 0, monthly: 0, accum: 0, net: 0 }
    cur.count += 1
    cur.original += row._original
    cur.monthly += row._monthly
    cur.accum += row._accum
    cur.net += row._net
    map.set(row.use_dept, cur)
  }
  return [...map.entries()].map(([dept, v]) => ({
    use_dept: dept,
    asset_count: String(v.count),
    original_value: formatMoney(v.original),
    monthly_amount: formatMoney(v.monthly),
    accum_depr: formatMoney(v.accum),
    net_value: formatMoney(v.net),
    _count: v.count,
    _original: v.original,
    _monthly: v.monthly,
    _accum: v.accum,
    _net: v.net
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

const filteredDetailRows = computed(() => {
  let rows = [...allDetailRows.value]
  const code = detailFilters.deviceCode.trim()
  const name = detailFilters.assetName.trim()
  const dept = detailFilters.useDept.trim()
  if (code) rows = rows.filter((r) => r.device_code.includes(code))
  if (name) rows = rows.filter((r) => r.asset_name.includes(name))
  if (dept) rows = rows.filter((r) => r.use_dept.includes(dept))

  const { prop, order } = detailSort.value
  if (prop && order) {
    const factor = order === 'ascending' ? 1 : -1
    const numKey: Record<string, keyof DetailRow> = {
      monthly_rate: '_rate',
      monthly_amount: '_monthly',
      original_value: '_original'
    }
    const field = numKey[prop]
    rows.sort((a, b) => {
      if (field) return (Number(a[field]) - Number(b[field])) * factor
      const av = String((a as Record<string, unknown>)[prop] ?? '')
      const bv = String((b as Record<string, unknown>)[prop] ?? '')
      return av.localeCompare(bv, 'zh-CN') * factor
    })
  }
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
    totalQty: rows.length,
    totalAmount: formatMoney(rows.reduce((s, r) => s + r._original, 0)),
    pageQty: page.length,
    pageAmount: formatMoney(page.reduce((s, r) => s + r._original, 0))
  }
})

const filteredSummaryRows = computed(() => {
  let rows = [...allSummaryRows.value]
  const dept = summaryFilters.useDept.trim()
  if (dept) rows = rows.filter((r) => r.use_dept.includes(dept))
  const { prop, order } = summarySort.value
  if (prop && order) {
    const factor = order === 'ascending' ? 1 : -1
    const numKey: Record<string, keyof SummaryRow> = {
      asset_count: '_count',
      original_value: '_original',
      monthly_amount: '_monthly'
    }
    const field = numKey[prop]
    rows.sort((a, b) => {
      if (field) return (Number(a[field]) - Number(b[field])) * factor
      return a.use_dept.localeCompare(b.use_dept, 'zh-CN') * factor
    })
  }
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
    totalQty: rows.reduce((s, r) => s + r._count, 0),
    totalAmount: formatMoney(rows.reduce((s, r) => s + r._original, 0)),
    pageQty: page.reduce((s, r) => s + r._count, 0),
    pageAmount: formatMoney(page.reduce((s, r) => s + r._original, 0))
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
  detailFilters.deviceCode = ''
  detailFilters.assetName = ''
  detailFilters.useDept = ''
  detailPage.value = 1
}
function onSummarySearch() {
  summaryPage.value = 1
}
function onSummaryReset() {
  summaryFilters.useDept = ''
  summaryPage.value = 1
}

function onDetailExport() {
  const rows = filteredDetailRows.value
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  let totalMonthly = 0
  let totalOriginal = 0
  let totalAccum = 0
  let totalNet = 0
  const body = rows
    .map((r, i) => {
      totalMonthly += r._monthly
      totalOriginal += r._original
      totalAccum += r._accum
      totalNet += r._net
      return `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.device_code)}</td>
      <td>${escapeHtml(r.asset_name)}</td>
      <td>${escapeHtml(r.specification)}</td>
      <td>${escapeHtml(r.unit)}</td>
      <td>${escapeHtml(r.use_dept)}</td>
      <td>${escapeHtml(r.monthly_rate)}</td>
      <td>${escapeHtml(r.monthly_amount)}</td>
      <td>${escapeHtml(r.original_value)}</td>
      <td>${escapeHtml(r.accum_depr)}</td>
      <td>${escapeHtml(r.net_value)}</td>
      <td>${escapeHtml(r.purchase_date)}</td>
      <td>${escapeHtml(r.depr_dept)}</td>
      <td>${escapeHtml(r.depr_ratio)}</td>
      <td>${escapeHtml(r.depr_months)}</td>
      <td>${escapeHtml(r.storage_place)}</td>
    </tr>`
    })
    .join('')
  const totalRow = `<tr>
      <td></td><td></td><td></td><td></td><td></td>
      <td><b>合计</b></td>
      <td></td>
      <td><b>${escapeHtml(formatMoney(totalMonthly))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalOriginal))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalAccum))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalNet))}</b></td>
      <td></td><td></td><td></td><td></td><td></td>
    </tr>`
  const table = `<table border="1">
    <thead>
      <tr>
        <th>序号</th><th>设备编号</th><th>资产名称</th><th>规格/型号</th><th>单位</th>
        <th>使用科室</th><th>月折旧率(%)</th><th>月折旧金额（元）</th><th>原值（元）</th>
        <th>累计折旧金额（元）</th><th>净值（元）</th><th>购入日期</th>
        <th>折旧科室</th><th>折旧比例</th><th>已折旧月份</th><th>存放地点</th>
      </tr>
    </thead>
    <tbody>${body}${totalRow}</tbody>
  </table>`
  downloadExcelHtml(table, `折旧明细比例-明细表${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

function onSummaryExport() {
  const rows = filteredSummaryRows.value
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  let totalCount = 0
  let totalOriginal = 0
  let totalMonthly = 0
  let totalAccum = 0
  let totalNet = 0
  const body = rows
    .map((r, i) => {
      totalCount += r._count
      totalOriginal += r._original
      totalMonthly += r._monthly
      totalAccum += r._accum
      totalNet += r._net
      return `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.use_dept)}</td>
      <td>${escapeHtml(r.asset_count)}</td>
      <td>${escapeHtml(r.original_value)}</td>
      <td>${escapeHtml(r.monthly_amount)}</td>
      <td>${escapeHtml(r.accum_depr)}</td>
      <td>${escapeHtml(r.net_value)}</td>
    </tr>`
    })
    .join('')
  const totalRow = `<tr>
      <td></td>
      <td><b>合计</b></td>
      <td><b>${totalCount}</b></td>
      <td><b>${escapeHtml(formatMoney(totalOriginal))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalMonthly))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalAccum))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalNet))}</b></td>
    </tr>`
  const table = `<table border="1">
    <thead>
      <tr>
        <th>序号</th><th>使用科室</th><th>资产数量</th>
        <th>原值合计（元）</th><th>月折旧合计（元）</th>
        <th>累计折旧合计（元）</th><th>净值合计（元）</th>
      </tr>
    </thead>
    <tbody>${body}${totalRow}</tbody>
  </table>`
  downloadExcelHtml(table, `折旧明细比例-汇总表${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}
</script>

<style scoped>
.depr-ratio-report {
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
  border: 1px solid #b7d4ea;
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
  border-bottom: 1px solid #d0d7de;
}

.query-box > .section-bar {
  background: #d6ebf8;
  border-bottom: 1px solid #b7d4ea;
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
  border: 1px solid #d0d7de;
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
  --el-table-border-color: #d0d7de;
  --el-table-header-bg-color: #d6ebf8;
  --el-table-tr-bg-color: #ffffff;
  --el-table-row-hover-bg-color: #e6f7ff;
}

.table-wrap :deep(.el-table::before),
.table-wrap :deep(.el-table--border::after) {
  display: none;
}

.table-wrap :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.table-wrap :deep(.el-table th.el-table__cell) {
  background-color: #d6ebf8 !important;
  color: #303133 !important;
  font-weight: 400;
}

.table-wrap :deep(.el-table th.el-table__cell .cell) {
  color: #303133 !important;
  font-weight: 400;
}

.table-wrap :deep(.el-table td.el-table__cell) {
  background-color: #ffffff;
  color: #303133;
  padding: 10px 0;
}

.table-wrap :deep(.el-table__row--striped td.el-table__cell) {
  background-color: #fef5e7;
}

.table-wrap :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: #e6f7ff !important;
}

.table-wrap :deep(.el-table--border .el-table__cell) {
  border-right-color: #d0d7de;
}

.table-wrap :deep(.el-table td.el-table__cell),
.table-wrap :deep(.el-table th.el-table__cell.is-leaf) {
  border-bottom-color: #d0d7de;
}

.table-wrap :deep(.el-table--border::before),
.table-wrap :deep(.el-table__border-left-patch) {
  background-color: #d0d7de;
}

.table-footer {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: #d6ebf8;
  border-top: 1px solid #d0d7de;
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
