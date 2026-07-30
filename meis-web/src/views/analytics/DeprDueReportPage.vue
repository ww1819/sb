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
                  <el-button @click="onFundExport">导出</el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar section-bar--with-help">
          <span>折旧到期汇总-资产折旧汇总(资金)</span>
          <el-popover
            placement="bottom-start"
            :width="420"
            trigger="hover"
            :show-after="150"
            popper-class="depr-fund-formula-popper"
          >
            <template #reference>
              <el-button class="section-help-btn" circle size="small" title="数据来源公式解析">
                <span class="section-help-mark">?</span>
              </el-button>
            </template>
            <div class="formula-popover">
              <div class="formula-popover-title">数据来源公式解析</div>
              <ul class="formula-list">
                <li>期初数据 = 上期期末（第一次统计时为 0）</li>
                <li>本期增加 = 启用日期为上期至本期并且已启用折旧的资产统计</li>
                <li>本期减少 = 停用日期为本期并且已启用折旧的资产统计</li>
                <li>期末数据 = 期初数据 + 本期增加 − 本期减少</li>
              </ul>
            </div>
          </el-popover>
        </div>
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
          <el-table-column
            prop="accrue_original"
            label="计提原值(元)"
            min-width="140"
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
                <el-form-item label="使用状态">
                  <el-input v-model="statusFilters.useStatus" clearable placeholder="使用状态搜索" />
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
            prop="dept_code"
            label="科室编码"
            min-width="120"
            sortable="custom"
          />
          <el-table-column
            prop="dept_name"
            label="科室名称"
            min-width="140"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column
            prop="use_status"
            label="使用状态"
            min-width="110"
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
import { ElMessage } from 'element-plus'

const activeTab = ref<'fund' | 'grade' | 'status'>('fund')

const fundFilters = reactive({ fundSource: '' })
const gradeFilters = reactive({ gradeName: '' })
const statusFilters = reactive({ useStatus: '' })

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

type GradeRow = {
  original_value: string
  period_depr: string
  accum_depr: string
  net_value: string
  accrue_original: string
  _count: number
  _original: number
  _period_depr: number
  _accum: number
  _net: number
  _accrue: number
}

type StatusRow = {
  dept_code: string
  dept_name: string
  use_status: string
  original_value: string
  accum_depr: string
  net_value: string
  _count: number
  _original: number
  _accum: number
  _net: number
}

const MOCK_FUND_SOURCES = ['财政拨款', '自筹资金', '科研经费', '捐赠资金', '其他']
const MOCK_DEPTS = [
  { code: 'KS001', name: '放射科' },
  { code: 'KS002', name: '检验科' },
  { code: 'KS003', name: '手术室' },
  { code: 'KS004', name: 'ICU' },
  { code: 'KS005', name: '急诊科' },
  { code: 'KS006', name: '心内科' },
  { code: 'KS007', name: '骨科' },
  { code: 'KS008', name: '儿科' }
]
const MOCK_USE_STATUSES = ['在用', '闲置', '维修中', '已报废']
const MOCK_GRADES = ['房屋及建筑物', '专用设备', '一般设备', '交通运输设备', '其他固定资产']

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
  return MOCK_GRADES.map((_, i) => {
    const original = 800000 + i * 320000 + ((i + 1) * 45821) % 200000
    const periodDepr = Math.round(original * (0.02 + i * 0.005))
    const accum = Math.round(original * (0.28 + i * 0.05))
    const net = original - accum
    const accrue = Math.round(original * (0.85 + (i % 3) * 0.04))
    return {
      original_value: formatMoney(original),
      period_depr: formatMoney(periodDepr),
      accum_depr: formatMoney(accum),
      net_value: formatMoney(net),
      accrue_original: formatMoney(accrue),
      _count: 8 + i * 3,
      _original: original,
      _period_depr: periodDepr,
      _accum: accum,
      _net: net,
      _accrue: accrue
    }
  })
}

function buildMockStatusRows(count = 40): StatusRow[] {
  const rows: StatusRow[] = []
  for (let i = 1; i <= count; i++) {
    const dept = MOCK_DEPTS[(i - 1) % MOCK_DEPTS.length]
    const original = 120000 + ((i * 17359) % 880000)
    const accum = Math.round(original * (0.18 + (i % 50) / 100))
    const net = original - accum
    rows.push({
      dept_code: dept.code,
      dept_name: dept.name,
      use_status: MOCK_USE_STATUSES[(i - 1) % MOCK_USE_STATUSES.length],
      original_value: formatMoney(original),
      accum_depr: formatMoney(accum),
      net_value: formatMoney(net),
      _count: 1,
      _original: original,
      _accum: accum,
      _net: net
    })
  }
  return rows
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

const sortedGradeRows = computed(() => {
  const rows = [...allGradeRows.value]
  const { prop, order } = gradeSort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  const numKey: Record<string, keyof GradeRow> = {
    original_value: '_original',
    period_depr: '_period_depr',
    accum_depr: '_accum',
    net_value: '_net',
    accrue_original: '_accrue'
  }
  const field = numKey[prop]
  if (!field) return rows
  rows.sort((a, b) => (Number(a[field]) - Number(b[field])) * factor)
  return rows
})
const pagedGradeRows = computed(() => {
  const start = (gradePage.value - 1) * gradePageSize.value
  return sortedGradeRows.value.slice(start, start + gradePageSize.value)
})
const gradeSummary = computed(() => {
  const totalQty = allGradeRows.value.reduce((s, r) => s + r._count, 0)
  const totalAmount = allGradeRows.value.reduce((s, r) => s + r._original, 0)
  const pageQty = pagedGradeRows.value.reduce((s, r) => s + r._count, 0)
  const pageAmount = pagedGradeRows.value.reduce((s, r) => s + r._original, 0)
  return {
    totalQty,
    totalAmount: formatMoney(totalAmount),
    pageQty,
    pageAmount: formatMoney(pageAmount)
  }
})

const sortedStatusRows = computed(() => {
  const rows = [...allStatusRows.value]
  const { prop, order } = statusSort.value
  if (!prop || !order) return rows
  const factor = order === 'ascending' ? 1 : -1
  const numKey: Record<string, keyof StatusRow> = {
    original_value: '_original',
    accum_depr: '_accum',
    net_value: '_net'
  }
  const field = numKey[prop]
  if (field) {
    rows.sort((a, b) => (Number(a[field]) - Number(b[field])) * factor)
    return rows
  }
  rows.sort((a, b) => {
    const av = String((a as Record<string, unknown>)[prop] ?? '')
    const bv = String((b as Record<string, unknown>)[prop] ?? '')
    return av.localeCompare(bv, 'zh-CN') * factor
  })
  return rows
})
const pagedStatusRows = computed(() => {
  const start = (statusPage.value - 1) * statusPageSize.value
  return sortedStatusRows.value.slice(start, start + statusPageSize.value)
})
const statusSummary = computed(() => {
  const totalQty = allStatusRows.value.length
  const totalAmount = allStatusRows.value.reduce((s, r) => s + r._original, 0)
  const pageQty = pagedStatusRows.value.length
  const pageAmount = pagedStatusRows.value.reduce((s, r) => s + r._original, 0)
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

function formatExportDate(d = new Date()) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

function escapeHtml(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function downloadExcelHtml(htmlTable: string, filenameWithoutExt: string) {
  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8" /></head><body>${htmlTable}</body></html>`
  const blob = new Blob([`\uFEFF${html}`], { type: 'application/vnd.ms-excel;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filenameWithoutExt}.xls`
  a.click()
  URL.revokeObjectURL(url)
}

function onFundExport() {
  const rows = allFundRows.value
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const body = rows
    .map(
      (r, i) => `<tr>
      <td>${i + 1}</td>
      <td>${escapeHtml(r.fund_source)}</td>
      <td>${escapeHtml(r.amount1)}</td><td>${escapeHtml(r.depr1)}</td>
      <td>${escapeHtml(r.amount2)}</td><td>${escapeHtml(r.depr2)}</td>
      <td>${escapeHtml(r.amount3)}</td><td>${escapeHtml(r.depr3)}</td>
      <td>${escapeHtml(r.amount4)}</td><td>${escapeHtml(r.depr4)}</td>
    </tr>`
    )
    .join('')
  const table = `<table border="1">
    <thead>
      <tr>
        <th rowspan="2">序号</th>
        <th rowspan="2">资金来源</th>
        <th colspan="2">期初</th>
        <th colspan="2">本期增加</th>
        <th colspan="2">本期减少</th>
        <th colspan="2">期末</th>
      </tr>
      <tr>
        <th>资产金额(元)</th><th>累计折旧(元)</th>
        <th>资产金额(元)</th><th>累计折旧(元)</th>
        <th>资产金额(元)</th><th>累计折旧(元)</th>
        <th>资产金额(元)</th><th>累计折旧(元)</th>
      </tr>
    </thead>
    <tbody>${body}</tbody>
  </table>`
  const filename = `资产折旧汇总(资金)${formatExportDate()}`
  downloadExcelHtml(table, filename)
  ElMessage.success(`已导出 ${rows.length} 条`)
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
  statusFilters.useStatus = ''
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

.section-bar--with-help {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-help-btn {
  width: 22px !important;
  height: 22px !important;
  min-height: 22px !important;
  padding: 0 !important;
  border-color: #909399;
  color: #606266;
}

.section-help-mark {
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.formula-popover-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #f56c6c;
}

.formula-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.formula-list li {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #f56c6c;
}

.formula-list li:last-child {
  margin-bottom: 0;
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
