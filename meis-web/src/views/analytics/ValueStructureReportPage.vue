<template>
  <div class="value-structure-report">
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
                <el-form-item label="分类编号">
                  <el-input
                    v-model="detailFilters.categoryCode"
                    clearable
                    placeholder="设备分类编号搜索"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="设备类型">
                  <el-input
                    v-model="detailFilters.deviceType"
                    clearable
                    placeholder="设备类型搜索"
                  />
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
                <el-form-item label="数量">
                  <el-input
                    v-model="detailFilters.quantity"
                    clearable
                    placeholder="数量查询"
                    style="max-width: 320px"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <div class="table-wrap">
        <div class="section-bar">
          价值结构分析表-明细表
          <span class="section-bar-note">
            （注：原值百分比=期末原值/设备分类总值×100%净值率=期末净值/期末原值×100%）
          </span>
        </div>
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
            prop="category_code"
            label="设备分类编号"
            min-width="130"
            sortable="custom"
          />
          <el-table-column
            prop="device_type"
            label="设备类型"
            min-width="140"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column
            prop="quantity"
            label="数量"
            width="90"
            align="right"
            sortable="custom"
          />
          <el-table-column
            prop="original_value"
            label="期末原值（元）"
            min-width="130"
            align="right"
            sortable="custom"
          />
          <el-table-column prop="accum_depr" label="期末累计折旧(元)" min-width="140" align="right" />
          <el-table-column prop="net_value" label="期末净值（元)" min-width="130" align="right" />
          <el-table-column prop="original_pct" label="原值百分比(%)" width="120" align="right" />
          <el-table-column prop="net_rate" label="净值率(%)" width="110" align="right" />
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
                <el-form-item label="设备类型">
                  <el-input
                    v-model="summaryFilters.deviceType"
                    clearable
                    placeholder="设备类型搜索"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12" :md="8">
                <el-form-item label="净值率≥">
                  <el-input
                    v-model="summaryFilters.minNetRate"
                    clearable
                    placeholder="最低净值率，如 40"
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
        <div class="section-bar">
          价值结构分析表-汇总表
          <span class="section-bar-note">
            （注：原值百分比=期末原值/设备分类总值×100%净值率=期末净值/期末原值×100%）
          </span>
        </div>
        <el-table
          :data="summaryRows"
          border
          stripe
          empty-text="暂无数据（前端样式示意，未接后台）"
        >
          <el-table-column prop="device_type" label="设备类型" min-width="160" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量合计" width="110" align="right" />
          <el-table-column prop="original_value" label="期末原值合计（元）" min-width="150" align="right" />
          <el-table-column prop="net_value" label="期末净值合计（元）" min-width="150" align="right" />
          <el-table-column prop="original_pct" label="原值百分比(%)" width="120" align="right" />
          <el-table-column prop="avg_net_rate" label="平均净值率(%)" width="130" align="right" />
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

const activeTab = ref<'detail' | 'summary'>('detail')

const detailFilters = reactive({
  categoryCode: '',
  deviceType: '',
  quantity: ''
})

const summaryFilters = reactive({
  deviceType: '',
  minNetRate: ''
})

type DetailRow = {
  category_code: string
  device_type: string
  quantity: string
  original_value: string
  accum_depr: string
  net_value: string
  original_pct: string
  net_rate: string
  _qty: number
  _original: number
}

const MOCK_DEVICE_TYPES = [
  'CT机',
  'MRI',
  'DR数字化X光机',
  '超声诊断仪',
  '呼吸机',
  '麻醉机',
  '监护仪',
  '输液泵',
  '血液透析机',
  '生化分析仪',
  '血球仪',
  '心电图机',
  '除颤仪',
  '手术无影灯',
  '内窥镜系统',
  '牙科综合治疗台',
  '高压氧舱',
  '灭菌器',
  '冷藏冰箱',
  '移动护理车'
]

function formatMoney(n: number) {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 前端样式示意：50 条测试数据，不请求后台 */
function buildMockDetailRows(count = 50): DetailRow[] {
  const rows: DetailRow[] = []
  let totalOriginal = 0
  const raw: { original: number; depr: number; qty: number; type: string; code: string }[] = []

  for (let i = 1; i <= count; i++) {
    const original = 80000 + ((i * 13783) % 920000)
    const netRate = 25 + ((i * 7) % 70)
    const net = Math.round((original * netRate) / 100)
    const depr = original - net
    const qty = 1 + ((i * 3) % 28)
    raw.push({
      code: `SB-${String(i).padStart(3, '0')}`,
      type: MOCK_DEVICE_TYPES[(i - 1) % MOCK_DEVICE_TYPES.length],
      qty,
      original,
      depr
    })
    totalOriginal += original
  }

  for (const item of raw) {
    const net = item.original - item.depr
    rows.push({
      category_code: item.code,
      device_type: item.type,
      quantity: String(item.qty),
      original_value: formatMoney(item.original),
      accum_depr: formatMoney(item.depr),
      net_value: formatMoney(net),
      original_pct: ((item.original / totalOriginal) * 100).toFixed(2),
      net_rate: ((net / item.original) * 100).toFixed(2),
      _qty: item.qty,
      _original: item.original
    })
  }
  return rows
}

const allDetailRows = ref<DetailRow[]>(buildMockDetailRows(50))
const detailPage = ref(1)
const detailPageSize = ref(10)
const detailSort = ref<{ prop: string; order: 'ascending' | 'descending' | null }>({
  prop: '',
  order: null
})

const sortedDetailRows = computed(() => {
  const rows = [...allDetailRows.value]
  const { prop, order } = detailSort.value
  if (!prop || !order) return rows

  const factor = order === 'ascending' ? 1 : -1
  rows.sort((a, b) => {
    if (prop === 'quantity') return (a._qty - b._qty) * factor
    if (prop === 'original_value') return (a._original - b._original) * factor
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
  const totalQty = allDetailRows.value.reduce((s, r) => s + r._qty, 0)
  const totalAmount = allDetailRows.value.reduce((s, r) => s + r._original, 0)
  const pageQty = pagedDetailRows.value.reduce((s, r) => s + r._qty, 0)
  const pageAmount = pagedDetailRows.value.reduce((s, r) => s + r._original, 0)
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

function onDetailSortChange(payload: {
  prop: string
  order: 'ascending' | 'descending' | null
}) {
  detailSort.value = { prop: payload.prop || '', order: payload.order }
  detailPage.value = 1
}

const summaryRows = ref<
  {
    device_type: string
    quantity: string
    original_value: string
    net_value: string
    original_pct: string
    avg_net_rate: string
  }[]
>([])

function onDetailSearch() {
  // 本期不接后台；查询后回到第一页
  detailPage.value = 1
}

function onDetailReset() {
  detailFilters.categoryCode = ''
  detailFilters.deviceType = ''
  detailFilters.quantity = ''
  detailPage.value = 1
}

function onSummarySearch() {
  // 本期不接后台
}

function onSummaryReset() {
  summaryFilters.deviceType = ''
  summaryFilters.minNetRate = ''
}
</script>

<style scoped>
.value-structure-report {
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

.section-bar-note {
  font-weight: 400;
  color: #f56c6c;
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

/* 隔行色差：一行白、一行米黄 */
.table-wrap :deep(.el-table__row--striped td.el-table__cell) {
  background-color: #fef5e7;
}

/* 悬停高亮 */
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

/* 合计行（有数据后可用 class-name / row-class-name） */
.table-wrap :deep(.el-table tr.row-total td.el-table__cell) {
  background-color: #fff7e6 !important;
  font-weight: 600;
}

.table-footer {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: #fff7e6;
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
