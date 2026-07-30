<template>
  <div class="asset-usage-report">
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
                <el-form-item label="科室">
                  <el-input v-model="detailFilters.deptName" clearable placeholder="科室搜索" />
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
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="资产规格">
                  <el-input
                    v-model="detailFilters.specification"
                    clearable
                    placeholder="资产规格搜索"
                    style="max-width: 320px"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">资产使用率统计-明细表</div>
        <el-table
          :data="pagedDetailRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onDetailSortChange"
        >
          <el-table-column
            type="index"
            label="序号"
            width="64"
            align="center"
            :index="detailIndexMethod"
          />
          <el-table-column
            prop="asset_code"
            label="资产编号"
            min-width="140"
            sortable="custom"
          />
          <el-table-column
            prop="dept_name"
            label="科室"
            min-width="120"
            sortable="custom"
          />
          <el-table-column
            prop="asset_name"
            label="资产名称"
            min-width="160"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column
            prop="usage_rate"
            label="使用率"
            width="110"
            align="right"
            sortable="custom"
          />
          <el-table-column prop="specification" label="资产规格" min-width="140" show-overflow-tooltip />
          <el-table-column prop="price" label="资产价格" width="130" align="right" />
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
              :total="allDetailRows.length"
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
                  <el-input v-model="summaryFilters.deptName" clearable placeholder="科室搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="使用率≥">
                  <el-input
                    v-model="summaryFilters.minUsageRate"
                    clearable
                    placeholder="最低使用率，如 60"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
                <el-form-item label-width="0">
                  <el-button type="primary" @click="onSummarySearch">查询</el-button>
                  <el-button @click="onSummaryReset">重置</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">资产使用率统计-汇总表</div>
        <el-table
          :data="pagedSummaryRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onSummarySortChange"
        >
          <el-table-column
            type="index"
            label="序号"
            width="64"
            align="center"
            :index="summaryIndexMethod"
          />
          <el-table-column
            prop="dept_name"
            label="科室"
            min-width="160"
            sortable="custom"
          />
          <el-table-column
            prop="asset_count"
            label="资产数量"
            width="120"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="avg_usage_rate"
            label="平均使用率"
            width="130"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="total_price"
            label="资产总价"
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
              :total="allSummaryRows.length"
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

const activeTab = ref<'detail' | 'summary'>('detail')

const detailFilters = reactive({
  deptName: '',
  assetName: '',
  specification: ''
})

const summaryFilters = reactive({
  deptName: '',
  minUsageRate: ''
})

type DetailRow = {
  asset_code: string
  dept_name: string
  asset_name: string
  usage_rate: string
  specification: string
  price: string
  _usage: number
  _price: number
}

type SummaryRow = {
  dept_name: string
  asset_count: string
  avg_usage_rate: string
  total_price: string
  _count: number
  _usage: number
  _price: number
}

const MOCK_DEPTS = ['放射科', '检验科', '手术室', 'ICU', '急诊科', '心内科', '骨科', '儿科']
const MOCK_ASSETS = [
  'CT机',
  'MRI',
  '超声诊断仪',
  '呼吸机',
  '监护仪',
  '输液泵',
  '麻醉机',
  '血液透析机',
  '心电图机',
  '除颤仪'
]
const MOCK_SPECS = ['标准型', '加强型', '便携式', '台式', '壁挂式', '车载式']

function formatMoney(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 前端样式示意：50 条明细测试数据，不请求后台 */
function buildMockDetailRows(count = 50): DetailRow[] {
  const rows: DetailRow[] = []
  for (let i = 1; i <= count; i++) {
    const price = 50000 + ((i * 17359) % 880000)
    const usage = 20 + ((i * 11) % 75)
    rows.push({
      asset_code: `ZC-${String(i).padStart(4, '0')}`,
      dept_name: MOCK_DEPTS[(i - 1) % MOCK_DEPTS.length],
      asset_name: MOCK_ASSETS[(i - 1) % MOCK_ASSETS.length],
      usage_rate: `${usage.toFixed(1)}%`,
      specification: MOCK_SPECS[(i - 1) % MOCK_SPECS.length],
      price: formatMoney(price),
      _usage: usage,
      _price: price
    })
  }
  return rows
}

/** 前端样式示意：汇总按科室聚合 */
function buildMockSummaryRows(details: DetailRow[]): SummaryRow[] {
  const map = new Map<string, { count: number; usageSum: number; price: number }>()
  for (const row of details) {
    const cur = map.get(row.dept_name) ?? { count: 0, usageSum: 0, price: 0 }
    cur.count += 1
    cur.usageSum += row._usage
    cur.price += row._price
    map.set(row.dept_name, cur)
  }
  return [...map.entries()].map(([dept, v]) => ({
    dept_name: dept,
    asset_count: String(v.count),
    avg_usage_rate: `${(v.usageSum / v.count).toFixed(1)}%`,
    total_price: formatMoney(v.price),
    _count: v.count,
    _usage: v.usageSum / v.count,
    _price: v.price
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

const sortedDetailRows = computed(() => {
  const rows = [...allDetailRows.value]
  const { prop, order } = detailSort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  rows.sort((a, b) => {
    if (prop === 'usage_rate') return (a._usage - b._usage) * factor
    if (prop === 'price') return (a._price - b._price) * factor
    const av = String((a as Record<string, unknown>)[prop] ?? '')
    const bv = String((b as Record<string, unknown>)[prop] ?? '')
    return av.localeCompare(bv, 'zh-CN') * factor
  })
  return rows
})

const pagedDetailRows = computed(() => {
  const start = (detailPage.value - 1) * detailPageSize.value
  return sortedDetailRows.value.slice(start, start + detailPageSize.value)
})

const detailSummary = computed(() => {
  const totalQty = allDetailRows.value.length
  const totalAmount = allDetailRows.value.reduce((s, r) => s + r._price, 0)
  const pageQty = pagedDetailRows.value.length
  const pageAmount = pagedDetailRows.value.reduce((s, r) => s + r._price, 0)
  return {
    totalQty,
    totalAmount: formatMoney(totalAmount),
    pageQty,
    pageAmount: formatMoney(pageAmount)
  }
})

const sortedSummaryRows = computed(() => {
  const rows = [...allSummaryRows.value]
  const { prop, order } = summarySort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  rows.sort((a, b) => {
    if (prop === 'asset_count') return (a._count - b._count) * factor
    if (prop === 'avg_usage_rate') return (a._usage - b._usage) * factor
    if (prop === 'total_price') return (a._price - b._price) * factor
    return a.dept_name.localeCompare(b.dept_name, 'zh-CN') * factor
  })
  return rows
})

const pagedSummaryRows = computed(() => {
  const start = (summaryPage.value - 1) * summaryPageSize.value
  return sortedSummaryRows.value.slice(start, start + summaryPageSize.value)
})

const summarySummary = computed(() => {
  const totalQty = allSummaryRows.value.reduce((s, r) => s + r._count, 0)
  const totalAmount = allSummaryRows.value.reduce((s, r) => s + r._price, 0)
  const pageQty = pagedSummaryRows.value.reduce((s, r) => s + r._count, 0)
  const pageAmount = pagedSummaryRows.value.reduce((s, r) => s + r._price, 0)
  return {
    totalQty,
    totalAmount: formatMoney(totalAmount),
    pageQty,
    pageAmount: formatMoney(pageAmount)
  }
})

function detailIndexMethod(index: number) {
  return (detailPage.value - 1) * detailPageSize.value + index + 1
}

function summaryIndexMethod(index: number) {
  return (summaryPage.value - 1) * summaryPageSize.value + index + 1
}

function onDetailSortChange(payload: {
  prop: string
  order: 'ascending' | 'descending' | null
}) {
  detailSort.value = { prop: payload.prop || '', order: payload.order }
  detailPage.value = 1
}

function onSummarySortChange(payload: {
  prop: string
  order: 'ascending' | 'descending' | null
}) {
  summarySort.value = { prop: payload.prop || '', order: payload.order }
  summaryPage.value = 1
}

function onDetailSearch() {
  detailPage.value = 1
}

function onDetailReset() {
  detailFilters.deptName = ''
  detailFilters.assetName = ''
  detailFilters.specification = ''
  detailPage.value = 1
}

function onSummarySearch() {
  summaryPage.value = 1
}

function onSummaryReset() {
  summaryFilters.deptName = ''
  summaryFilters.minUsageRate = ''
  summaryPage.value = 1
}
</script>

<style scoped>
.asset-usage-report {
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
