<template>
  <div class="depr-stats-report">
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
                <el-form-item label="价值区间">
                  <el-input
                    v-model="detailFilters.valueRange"
                    clearable
                    placeholder="资产价值区间搜索"
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
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">资产折旧统计-明细表</div>
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
          <el-table-column
            prop="value_range"
            label="资产价值区间"
            min-width="140"
            sortable="custom"
          />
          <el-table-column
            prop="original_value"
            label="原值(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="period_depr"
            label="本期折旧(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="accum_depr"
            label="累计折旧(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="net_value"
            label="净值(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
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
                <el-form-item label="价值区间">
                  <el-input
                    v-model="summaryFilters.valueRange"
                    clearable
                    placeholder="资产价值区间搜索"
                  />
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
        <div class="section-bar">资产折旧统计-汇总表</div>
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
          <el-table-column
            prop="value_range"
            label="资产价值区间"
            min-width="140"
            sortable="custom"
          />
          <el-table-column
            prop="original_value"
            label="原值(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="period_depr"
            label="本期折旧(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="accum_depr"
            label="累计折旧(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="net_value"
            label="净值(元)"
            min-width="140"
            align="right"
            sortable="custom"
          />
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

const detailFilters = reactive({ valueRange: '' })
const summaryFilters = reactive({ valueRange: '' })

type RangeRow = {
  value_range: string
  original_value: string
  period_depr: string
  accum_depr: string
  net_value: string
  _count: number
  _original: number
  _period: number
  _accum: number
  _net: number
}

/** 固定价值区间（示意） */
const VALUE_RANGES = ['万元以下', '1-10万', '10-20万', '20-50万', '50万元以上'] as const

function formatMoney(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function buildMockRangeRows(seedOffset = 0): RangeRow[] {
  return VALUE_RANGES.map((name, i) => {
    const original = 280000 + (i + 1 + seedOffset) * 410000 + ((i + 3) * 29173) % 180000
    const period = Math.round(original * (0.015 + i * 0.004))
    const accum = Math.round(original * (0.22 + i * 0.06))
    const net = original - accum
    return {
      value_range: name,
      original_value: formatMoney(original),
      period_depr: formatMoney(period),
      accum_depr: formatMoney(accum),
      net_value: formatMoney(net),
      _count: 12 + i * 7,
      _original: original,
      _period: period,
      _accum: accum,
      _net: net
    }
  })
}

const allDetailRows = ref<RangeRow[]>(buildMockRangeRows(0))
const allSummaryRows = ref<RangeRow[]>(buildMockRangeRows(2))

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

function filterByRange(rows: RangeRow[], keyword: string) {
  const kw = keyword.trim()
  if (!kw) return rows
  return rows.filter((r) => r.value_range.includes(kw))
}

function sortRangeRows(
  rows: RangeRow[],
  sort: { prop: string; order: 'ascending' | 'descending' | null }
) {
  const list = [...rows]
  const { prop, order } = sort
  if (!prop || !order) return list
  const factor = order === 'ascending' ? 1 : -1
  const numKey: Record<string, keyof RangeRow> = {
    original_value: '_original',
    period_depr: '_period',
    accum_depr: '_accum',
    net_value: '_net'
  }
  const field = numKey[prop]
  if (field) {
    list.sort((a, b) => (Number(a[field]) - Number(b[field])) * factor)
    return list
  }
  list.sort((a, b) => a.value_range.localeCompare(b.value_range, 'zh-CN') * factor)
  return list
}

const filteredDetailRows = computed(() =>
  sortRangeRows(filterByRange(allDetailRows.value, detailFilters.valueRange), detailSort.value)
)
const pagedDetailRows = computed(() => {
  const start = (detailPage.value - 1) * detailPageSize.value
  return filteredDetailRows.value.slice(start, start + detailPageSize.value)
})
const detailSummary = computed(() => {
  const rows = filteredDetailRows.value
  const page = pagedDetailRows.value
  return {
    totalQty: rows.reduce((s, r) => s + r._count, 0),
    totalAmount: formatMoney(rows.reduce((s, r) => s + r._original, 0)),
    pageQty: page.reduce((s, r) => s + r._count, 0),
    pageAmount: formatMoney(page.reduce((s, r) => s + r._original, 0))
  }
})

const filteredSummaryRows = computed(() =>
  sortRangeRows(filterByRange(allSummaryRows.value, summaryFilters.valueRange), summarySort.value)
)
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
  detailFilters.valueRange = ''
  detailPage.value = 1
}
function onSummarySearch() {
  summaryPage.value = 1
}
function onSummaryReset() {
  summaryFilters.valueRange = ''
  summaryPage.value = 1
}

function exportRangeRows(rows: RangeRow[], filenamePrefix: string) {
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  let totalOriginal = 0
  let totalPeriod = 0
  let totalAccum = 0
  let totalNet = 0
  const body = rows
    .map((r, i) => {
      totalOriginal += r._original
      totalPeriod += r._period
      totalAccum += r._accum
      totalNet += r._net
      return `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.value_range)}</td>
      <td>${escapeHtml(r.original_value)}</td>
      <td>${escapeHtml(r.period_depr)}</td>
      <td>${escapeHtml(r.accum_depr)}</td>
      <td>${escapeHtml(r.net_value)}</td>
    </tr>`
    })
    .join('')
  const totalRow = `<tr>
      <td></td>
      <td><b>合计</b></td>
      <td><b>${escapeHtml(formatMoney(totalOriginal))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalPeriod))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalAccum))}</b></td>
      <td><b>${escapeHtml(formatMoney(totalNet))}</b></td>
    </tr>`
  const table = `<table border="1">
    <thead>
      <tr>
        <th>序号</th><th>资产价值区间</th><th>原值(元)</th>
        <th>本期折旧(元)</th><th>累计折旧(元)</th><th>净值(元)</th>
      </tr>
    </thead>
    <tbody>${body}${totalRow}</tbody>
  </table>`
  downloadExcelHtml(table, `${filenamePrefix}${formatExportDate()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

function onDetailExport() {
  exportRangeRows(filteredDetailRows.value, '资产折旧统计-明细表')
}
function onSummaryExport() {
  exportRangeRows(filteredSummaryRows.value, '资产折旧统计-汇总表')
}
</script>

<style scoped>
.depr-stats-report {
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
