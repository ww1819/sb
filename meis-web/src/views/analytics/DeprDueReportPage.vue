<template>
  <div class="depr-due-report">
    <el-tabs v-model="activeTab" class="report-tabs">
      <el-tab-pane label="资产折旧汇总(资金)" name="fund" />
      <el-tab-pane label="折旧汇总(分级)" name="grade" />
      <el-tab-pane label="折旧汇总(状态)" name="status" />
    </el-tabs>

    <!-- 资产折旧汇总(资金) -->
    <template v-if="activeTab === 'fund'">
      <div class="query-box">
        <div class="section-bar">查询条件</div>
        <div class="query-body">
          <el-form :model="fundFilters" class="filter-form" label-width="100px" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="资金来源">
                  <el-input v-model="fundFilters.fundSource" clearable placeholder="资金来源搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
                <el-form-item label-width="0">
                  <el-button type="primary" @click="onFundSearch">查询</el-button>
                  <el-button @click="onFundReset">重置</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">折旧到期汇总-资产折旧汇总(资金)</div>
        <el-table
          :data="pagedFundRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onFundSortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="fundIndexMethod" />
          <el-table-column
            prop="fund_source"
            label="资金来源"
            min-width="140"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column label="期初" align="center">
            <el-table-column
              prop="amount1"
              label="资产金额(元)"
              min-width="130"
              align="right"
              sortable="custom"
            />
            <el-table-column
              prop="depr1"
              label="累计折旧(元)"
              min-width="130"
              align="right"
              sortable="custom"
            />
          </el-table-column>
          <el-table-column label="本期增加" align="center">
            <el-table-column prop="amount2" label="资产金额(元)" min-width="130" align="right" />
            <el-table-column prop="depr2" label="累计折旧(元)" min-width="130" align="right" />
          </el-table-column>
          <el-table-column label="本期减少" align="center">
            <el-table-column prop="amount3" label="资产金额(元)" min-width="130" align="right" />
            <el-table-column prop="depr3" label="累计折旧(元)" min-width="130" align="right" />
          </el-table-column>
          <el-table-column label="期末" align="center">
            <el-table-column prop="amount4" label="资产金额(元)" min-width="130" align="right" />
            <el-table-column prop="depr4" label="累计折旧(元)" min-width="130" align="right" />
          </el-table-column>
        </el-table>
        <div class="table-footer">
          <div class="table-footer-summary">
            <span>合计：</span>
            <span>总数量：{{ fundSummary.totalQty }}</span>
            <span>总金额：{{ fundSummary.totalAmount }}</span>
            <span>当前页面数量：{{ fundSummary.pageQty }}</span>
            <span>当前页面金额：{{ fundSummary.pageAmount }}</span>
          </div>
          <div class="table-footer-pager">
            <el-pagination
              v-model:current-page="fundPage"
              v-model:page-size="fundPageSize"
              :page-sizes="[10, 20, 30, 50]"
              :total="allFundRows.length"
              layout="total, sizes, prev, pager, next, jumper"
              background
            />
          </div>
        </div>
      </div>
    </template>

    <!-- 折旧汇总(分级) -->
    <template v-else-if="activeTab === 'grade'">
      <div class="query-box">
        <div class="section-bar">查询条件</div>
        <div class="query-body">
          <el-form :model="gradeFilters" class="filter-form" label-width="100px" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="资产分级">
                  <el-input v-model="gradeFilters.gradeName" clearable placeholder="资产分级搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
                <el-form-item label-width="0">
                  <el-button type="primary" @click="onGradeSearch">查询</el-button>
                  <el-button @click="onGradeReset">重置</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">折旧到期汇总-折旧汇总(分级)</div>
        <el-table
          :data="pagedGradeRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onGradeSortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="gradeIndexMethod" />
          <el-table-column
            prop="grade_name"
            label="资产分级"
            min-width="160"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column
            prop="total_amount"
            label="资产金额合计(元)"
            min-width="150"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="total_depr"
            label="累计折旧合计(元)"
            min-width="150"
            align="right"
            sortable="custom"
          />
        </el-table>
        <div class="table-footer">
          <div class="table-footer-summary">
            <span>合计：</span>
            <span>总数量：{{ gradeSummary.totalQty }}</span>
            <span>总金额：{{ gradeSummary.totalAmount }}</span>
            <span>当前页面数量：{{ gradeSummary.pageQty }}</span>
            <span>当前页面金额：{{ gradeSummary.pageAmount }}</span>
          </div>
          <div class="table-footer-pager">
            <el-pagination
              v-model:current-page="gradePage"
              v-model:page-size="gradePageSize"
              :page-sizes="[10, 20, 30, 50]"
              :total="allGradeRows.length"
              layout="total, sizes, prev, pager, next, jumper"
              background
            />
          </div>
        </div>
      </div>
    </template>

    <!-- 折旧汇总(状态) -->
    <template v-else>
      <div class="query-box">
        <div class="section-bar">查询条件</div>
        <div class="query-body">
          <el-form :model="statusFilters" class="filter-form" label-width="100px" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="折旧状态">
                  <el-input v-model="statusFilters.statusName" clearable placeholder="折旧状态搜索" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
                <el-form-item label-width="0">
                  <el-button type="primary" @click="onStatusSearch">查询</el-button>
                  <el-button @click="onStatusReset">重置</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">折旧到期汇总-折旧汇总(状态)</div>
        <el-table
          :data="pagedStatusRows"
          border
          stripe
          height="100%"
          class="detail-table"
          empty-text="暂无数据（前端样式示意，未接后台）"
          @sort-change="onStatusSortChange"
        >
          <el-table-column type="index" label="序号" width="64" align="center" :index="statusIndexMethod" />
          <el-table-column
            prop="status_name"
            label="折旧状态"
            min-width="160"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column
            prop="total_amount"
            label="资产金额合计(元)"
            min-width="150"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="total_depr"
            label="累计折旧合计(元)"
            min-width="150"
            align="right"
            sortable="custom"
          />
        </el-table>
        <div class="table-footer">
          <div class="table-footer-summary">
            <span>合计：</span>
            <span>总数量：{{ statusSummary.totalQty }}</span>
            <span>总金额：{{ statusSummary.totalAmount }}</span>
            <span>当前页面数量：{{ statusSummary.pageQty }}</span>
            <span>当前页面金额：{{ statusSummary.pageAmount }}</span>
          </div>
          <div class="table-footer-pager">
            <el-pagination
              v-model:current-page="statusPage"
              v-model:page-size="statusPageSize"
              :page-sizes="[10, 20, 30, 50]"
              :total="allStatusRows.length"
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

const activeTab = ref<'fund' | 'grade' | 'status'>('fund')

const fundFilters = reactive({ fundSource: '' })
const gradeFilters = reactive({ gradeName: '' })
const statusFilters = reactive({ statusName: '' })

type FundRow = {
  fund_source: string
  amount1: string
  depr1: string
  amount2: string
  depr2: string
  amount3: string
  depr3: string
  amount4: string
  depr4: string
  _amount1: number
  _depr1: number
  _totalAmount: number
}

type AggRow = {
  key: string
  total_amount: string
  total_depr: string
  _count: number
  _amount: number
  _depr: number
}

type GradeRow = AggRow & { grade_name: string }
type StatusRow = AggRow & { status_name: string }

const MOCK_FUND_SOURCES = ['财政拨款', '自筹资金', '科研经费', '捐赠资金', '其他']
const MOCK_GRADES = ['房屋及建筑物', '专用设备', '一般设备', '交通运输设备', '其他固定资产']
const MOCK_STATUSES = ['正常折旧', '即将到期', '已到期', '已提足']

function formatMoney(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function moneyPair(seed: number) {
  const amount = 40000 + ((seed * 13783) % 680000)
  const depr = Math.round(amount * (0.15 + (seed % 40) / 100))
  return { amount, depr }
}

function buildMockFundRows(count = 50): FundRow[] {
  const rows: FundRow[] = []
  for (let i = 1; i <= count; i++) {
    const p1 = moneyPair(i)
    const p2 = moneyPair(i + 17)
    const p3 = moneyPair(i + 31)
    const p4 = moneyPair(i + 47)
    rows.push({
      fund_source: MOCK_FUND_SOURCES[(i - 1) % MOCK_FUND_SOURCES.length],
      amount1: formatMoney(p1.amount),
      depr1: formatMoney(p1.depr),
      amount2: formatMoney(p2.amount),
      depr2: formatMoney(p2.depr),
      amount3: formatMoney(p3.amount),
      depr3: formatMoney(p3.depr),
      amount4: formatMoney(p4.amount),
      depr4: formatMoney(p4.depr),
      _amount1: p1.amount,
      _depr1: p1.depr,
      _totalAmount: p1.amount + p2.amount + p3.amount + p4.amount
    })
  }
  return rows
}

function buildMockGradeRows(): GradeRow[] {
  return MOCK_GRADES.map((name, i) => {
    const amount = 800000 + i * 320000 + ((i + 1) * 45821) % 200000
    const depr = Math.round(amount * (0.28 + i * 0.05))
    return {
      key: name,
      grade_name: name,
      total_amount: formatMoney(amount),
      total_depr: formatMoney(depr),
      _count: 8 + i * 3,
      _amount: amount,
      _depr: depr
    }
  })
}

function buildMockStatusRows(): StatusRow[] {
  return MOCK_STATUSES.map((name, i) => {
    const amount = 600000 + i * 410000 + ((i + 2) * 29173) % 180000
    const depr = Math.round(amount * (0.2 + i * 0.12))
    return {
      key: name,
      status_name: name,
      total_amount: formatMoney(amount),
      total_depr: formatMoney(depr),
      _count: 10 + i * 5,
      _amount: amount,
      _depr: depr
    }
  })
}

const allFundRows = ref<FundRow[]>(buildMockFundRows(50))
const allGradeRows = ref<GradeRow[]>(buildMockGradeRows())
const allStatusRows = ref<StatusRow[]>(buildMockStatusRows())

const fundPage = ref(1)
const fundPageSize = ref(10)
const fundSort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null
})

const gradePage = ref(1)
const gradePageSize = ref(10)
const gradeSort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null
})

const statusPage = ref(1)
const statusPageSize = ref(10)
const statusSort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null
})

const sortedFundRows = computed(() => {
  const rows = [...allFundRows.value]
  const { prop, order } = fundSort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  rows.sort((a, b) => {
    if (prop === 'amount1') return (a._amount1 - b._amount1) * factor
    if (prop === 'depr1') return (a._depr1 - b._depr1) * factor
    if (prop === 'fund_source') return a.fund_source.localeCompare(b.fund_source, 'zh-CN') * factor
    return 0
  })
  return rows
})

const pagedFundRows = computed(() => {
  const start = (fundPage.value - 1) * fundPageSize.value
  return sortedFundRows.value.slice(start, start + fundPageSize.value)
})

const fundSummary = computed(() => {
  const totalQty = allFundRows.value.length
  const totalAmount = allFundRows.value.reduce((s, r) => s + r._totalAmount, 0)
  const pageQty = pagedFundRows.value.length
  const pageAmount = pagedFundRows.value.reduce((s, r) => s + r._totalAmount, 0)
  return {
    totalQty,
    totalAmount: formatMoney(totalAmount),
    pageQty,
    pageAmount: formatMoney(pageAmount)
  }
})

function sortAggRows<T extends AggRow>(
  rows: T[],
  sort: { prop: string; order: 'ascending' | 'descending' | null },
  nameProp: keyof T
) {
  const list = [...rows]
  const { prop, order } = sort
  if (!prop || !order) return list
  const factor = order === 'ascending' ? 1 : -1
  list.sort((a, b) => {
    if (prop === 'total_amount') return (a._amount - b._amount) * factor
    if (prop === 'total_depr') return (a._depr - b._depr) * factor
    const av = String(a[nameProp] ?? '')
    const bv = String(b[nameProp] ?? '')
    return av.localeCompare(bv, 'zh-CN') * factor
  })
  return list
}

const sortedGradeRows = computed(() =>
  sortAggRows(allGradeRows.value, gradeSort.value, 'grade_name')
)
const pagedGradeRows = computed(() => {
  const start = (gradePage.value - 1) * gradePageSize.value
  return sortedGradeRows.value.slice(start, start + gradePageSize.value)
})
const gradeSummary = computed(() => {
  const totalQty = allGradeRows.value.reduce((s, r) => s + r._count, 0)
  const totalAmount = allGradeRows.value.reduce((s, r) => s + r._amount, 0)
  const pageQty = pagedGradeRows.value.reduce((s, r) => s + r._count, 0)
  const pageAmount = pagedGradeRows.value.reduce((s, r) => s + r._amount, 0)
  return {
    totalQty,
    totalAmount: formatMoney(totalAmount),
    pageQty,
    pageAmount: formatMoney(pageAmount)
  }
})

const sortedStatusRows = computed(() =>
  sortAggRows(allStatusRows.value, statusSort.value, 'status_name')
)
const pagedStatusRows = computed(() => {
  const start = (statusPage.value - 1) * statusPageSize.value
  return sortedStatusRows.value.slice(start, start + statusPageSize.value)
})
const statusSummary = computed(() => {
  const totalQty = allStatusRows.value.reduce((s, r) => s + r._count, 0)
  const totalAmount = allStatusRows.value.reduce((s, r) => s + r._amount, 0)
  const pageQty = pagedStatusRows.value.reduce((s, r) => s + r._count, 0)
  const pageAmount = pagedStatusRows.value.reduce((s, r) => s + r._amount, 0)
  return {
    totalQty,
    totalAmount: formatMoney(totalAmount),
    pageQty,
    pageAmount: formatMoney(pageAmount)
  }
})

function fundIndexMethod(index: number) {
  return (fundPage.value - 1) * fundPageSize.value + index + 1
}
function gradeIndexMethod(index: number) {
  return (gradePage.value - 1) * gradePageSize.value + index + 1
}
function statusIndexMethod(index: number) {
  return (statusPage.value - 1) * statusPageSize.value + index + 1
}

function onFundSortChange(payload: { prop: string; order: 'ascending' | 'descending' | null }) {
  fundSort.value = { prop: payload.prop || '', order: payload.order }
  fundPage.value = 1
}
function onGradeSortChange(payload: { prop: string; order: 'ascending' | 'descending' | null }) {
  gradeSort.value = { prop: payload.prop || '', order: payload.order }
  gradePage.value = 1
}
function onStatusSortChange(payload: { prop: string; order: 'ascending' | 'descending' | null }) {
  statusSort.value = { prop: payload.prop || '', order: payload.order }
  statusPage.value = 1
}

function onFundSearch() {
  fundPage.value = 1
}
function onFundReset() {
  fundFilters.fundSource = ''
  fundPage.value = 1
}
function onGradeSearch() {
  gradePage.value = 1
}
function onGradeReset() {
  gradeFilters.gradeName = ''
  gradePage.value = 1
}
function onStatusSearch() {
  statusPage.value = 1
}
function onStatusReset() {
  statusFilters.statusName = ''
  statusPage.value = 1
}
</script>

<style scoped>
.depr-due-report {
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
