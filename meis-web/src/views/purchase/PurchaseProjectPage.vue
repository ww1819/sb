<template>
  <div class="purchase-project-page">
    <SystemPageCard
      title="设备采购计划表"
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
          placeholder="订单号 / 计划单号 / 科室 / 设备名称"
          @search="onSearch"
          @reset="onReset"
        >
          <template #actions>
            <el-button type="primary" @click="openBatchReview">审核</el-button>
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
        <el-table-column prop="order_no" label="订单号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.order_no || '-' }}</template>
        </el-table-column>
        <el-table-column prop="plan_code" label="计划单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="plan_year" label="年度" width="90" />
        <el-table-column prop="dept_name" label="申请科室" min-width="120" show-overflow-tooltip />
        <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="estimated_price" label="预算单价" min-width="110" align="right">
          <template #default="{ row }">
            <TableCellValue :field="priceField" :value="row.estimated_price" />
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right">
          <template #default="{ row }">
            <TableCellValue :field="qtyField" :value="row.quantity" />
          </template>
        </el-table-column>
        <el-table-column prop="total_price" label="总价值" min-width="120" align="right">
          <template #default="{ row }">
            <TableCellValue :field="totalField" :value="row.total_price" />
          </template>
        </el-table-column>
        <el-table-column prop="submitted_at" label="提交日期" width="120">
          <template #default="{ row }">{{ formatDay(row.submitted_at) }}</template>
        </el-table-column>
        <el-table-column prop="fund_source" label="经费来源" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <TableCellValue :field="fundField" :value="row.fund_source" />
          </template>
        </el-table-column>
        <el-table-column prop="purchase_purpose" label="购买用途" min-width="140" show-overflow-tooltip />
        <el-table-column prop="fill_date" label="申请日期" width="120">
          <template #default="{ row }">{{ formatDay(row.fill_date) }}</template>
        </el-table-column>
        <el-table-column prop="approval_comment" label="审核建议" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.approval_comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="order_review_comment" label="订单审核意见" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.order_review_comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="brand_intent" label="品牌意向" min-width="120" show-overflow-tooltip />
        <el-table-column prop="plan_remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.plan_remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReview(row)">审核</el-button>
            <el-button link type="primary" @click="openBargain(row)">议价</el-button>
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>

    <AppModal v-model="reviewVisible" title="订单审核" size="sm">
      <el-form label-width="110px">
        <el-form-item v-if="reviewMode === 'single'" label="订单号">
          <span>{{ reviewRow?.order_no || '-' }}</span>
        </el-form-item>
        <el-form-item v-else label="已选">
          <span>{{ selectedCount }} 条明细</span>
        </el-form-item>
        <el-form-item label="订单审核意见" required>
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请填写订单审核意见"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">确认审核</el-button>
      </template>
    </AppModal>

    <AppModal v-model="bargainVisible" title="询价议价" size="xxl">
      <div class="bargain-split">
        <div class="bargain-left">
          <el-table
            ref="bargainTableRef"
            :data="bargainItems"
            size="small"
            border
            highlight-current-row
            row-key="id"
            height="100%"
            class="bargain-item-table"
            table-layout="fixed"
          >
            <el-table-column type="index" label="序号" width="52" align="center" />
            <el-table-column label="状态" width="78" align="center">
              <template #default="{ row }">
                <span :class="isBargained(row) ? 'st-done' : 'st-todo'">
                  {{ isBargained(row) ? '已议价' : '未议价' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="dept_name" label="申请科室" width="110" show-overflow-tooltip />
            <el-table-column prop="device_name" label="设备名称" width="140" show-overflow-tooltip />
            <el-table-column prop="estimated_price" label="预计单价" width="100" align="right">
              <template #default="{ row }">
                <TableCellValue :field="priceField" :value="row.estimated_price" />
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="72" align="right">
              <template #default="{ row }">
                <TableCellValue :field="qtyField" :value="row.quantity" />
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="72" align="center" show-overflow-tooltip>
              <template #default="{ row }">{{ row.unit || '-' }}</template>
            </el-table-column>
            <el-table-column prop="specification" label="规格型号" width="160" show-overflow-tooltip />
          </el-table>
        </div>
        <div class="bargain-right">
          <div class="bargain-a4">
            <h3 class="bargain-a4__title">设备购置询价议价会议记录</h3>
            <table class="bargain-table">
              <tbody>
                <tr>
                  <th>会议地点</th>
                  <td>
                    <el-input v-model="bargainForm.bargain_meeting_location" placeholder="会议地点" />
                  </td>
                  <th>会议时间</th>
                  <td>
                    <el-date-picker
                      v-model="bargainForm.bargain_meeting_time"
                      type="date"
                      value-format="YYYY-MM-DD"
                      placeholder="选择日期"
                      style="width: 100%"
                    />
                  </td>
                </tr>
                <tr>
                  <th>申请科室</th>
                  <td>{{ bargainForm.dept_name || '-' }}</td>
                  <th>设备名称</th>
                  <td>{{ bargainForm.device_name || '-' }}</td>
                </tr>
                <tr>
                  <th>参与部门</th>
                  <td colspan="3">
                    <el-input v-model="bargainForm.bargain_participant_depts" placeholder="参与部门" />
                  </td>
                </tr>
                <tr class="bargain-tall">
                  <th>设备科意见</th>
                  <td colspan="3">
                    <el-input
                      v-model="bargainForm.bargain_dept_opinion"
                      type="textarea"
                      :rows="8"
                      placeholder="请填写设备科意见"
                    />
                  </td>
                </tr>
                <tr class="bargain-tall">
                  <th>会议内容</th>
                  <td colspan="3">
                    <el-input
                      v-model="bargainForm.bargain_meeting_content"
                      type="textarea"
                      :rows="12"
                      placeholder="请填写会议内容"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="bargainVisible = false">取消</el-button>
        <el-button type="primary" :loading="bargainSubmitting" @click="submitBargain">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type ElTable } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import AppModal from '@/components/AppModal.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope } from '@/composables/useListActionScope'

const { loadDict, resolveDictLabel } = useDict()
const { selectedCount, selectedIds, syncFromTable, clearAll } = useCrossPageSelection()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const selectedRowMap = ref<Map<string, Record<string, unknown>>>(new Map())

const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewMode = ref<'single' | 'batch'>('single')
const reviewRow = ref<Record<string, unknown> | null>(null)
const reviewComment = ref('同意审核')

const bargainVisible = ref(false)
const bargainSubmitting = ref(false)
const bargainItemId = ref('')
const bargainDeptName = ref('')
const bargainItems = ref<Record<string, unknown>[]>([])
const bargainTableRef = ref<InstanceType<typeof ElTable> | null>(null)
const bargainForm = reactive({
  dept_name: '',
  device_name: '',
  bargain_meeting_location: '设备科',
  bargain_meeting_time: '',
  bargain_participant_depts: '',
  bargain_dept_opinion: '',
  bargain_meeting_content: ''
})

const priceField: FieldSchema = { prop: 'estimated_price', label: '预算单价', type: 'number' }
const qtyField: FieldSchema = { prop: 'quantity', label: '数量', type: 'number' }
const totalField: FieldSchema = { prop: 'total_price', label: '总价值', type: 'number' }
const fundField: FieldSchema = { prop: 'fund_source', label: '经费来源', dictType: 'fund_source' }

const today = computed(() => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

function formatDay(v: unknown) {
  if (v == null || v === '') return '-'
  const s = String(v)
  const m = s.match(/^(\d{4}-\d{2}-\d{2})/)
  if (m) return m[1]
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
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
    const { data } = await http.get('/purchase/project/page', {
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

function openReview(row: Record<string, unknown>) {
  reviewMode.value = 'single'
  reviewRow.value = row
  reviewComment.value = String(row.order_review_comment || '同意审核')
  reviewVisible.value = true
}

function openBatchReview() {
  if (selectedCount.value === 0) {
    ElMessage.warning('请先勾选要审核的明细')
    return
  }
  reviewMode.value = 'batch'
  reviewRow.value = null
  reviewComment.value = '同意审核'
  reviewVisible.value = true
}

async function submitReview() {
  const comment = reviewComment.value.trim()
  if (!comment) {
    ElMessage.warning('请填写订单审核意见')
    return
  }
  const targets =
    reviewMode.value === 'single'
      ? reviewRow.value?.id
        ? [String(reviewRow.value.id)]
        : []
      : selectedIds()
  if (!targets.length) {
    ElMessage.warning('没有可审核的明细')
    return
  }
  reviewSubmitting.value = true
  let ok = 0
  try {
    for (const id of targets) {
      await http.post(`/purchase/project/approved-items/${id}/order-review`, { comment })
      ok++
    }
    ElMessage.success(reviewMode.value === 'batch' ? `已审核 ${ok} 条` : '已保存订单审核意见')
    reviewVisible.value = false
    if (reviewMode.value === 'batch') {
      clearAll(tableRef.value)
      selectedRowMap.value = new Map()
    }
    load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || `审核失败（已成功 ${ok} 条）`)
  } finally {
    reviewSubmitting.value = false
  }
}

function isBargained(row: Record<string, unknown> | undefined) {
  if (!row) return false
  return !!(row.bargain_at || row.bargain_meeting_content || row.bargain_dept_opinion)
}

function fillBargainForm(row: Record<string, unknown>) {
  bargainItemId.value = String(row.id ?? '')
  const dept = String(row.dept_name ?? bargainDeptName.value ?? '')
  const device = String(row.device_name ?? '')
  bargainForm.dept_name = dept
  bargainForm.device_name = device
  bargainForm.bargain_meeting_location = String(row.bargain_meeting_location || '设备科')
  bargainForm.bargain_meeting_time = formatDay(row.bargain_meeting_time)
  if (bargainForm.bargain_meeting_time === '-') bargainForm.bargain_meeting_time = today.value
  bargainForm.bargain_participant_depts = String(
    row.bargain_participant_depts || (dept ? `设备科，${dept}` : '设备科')
  )
  bargainForm.bargain_dept_opinion = String(row.bargain_dept_opinion || '')
  bargainForm.bargain_meeting_content = String(
    row.bargain_meeting_content || (device ? `${device} 议价` : '')
  )
  nextTick(() => {
    bargainTableRef.value?.setCurrentRow(row)
  })
}

function openBargain(row: Record<string, unknown>) {
  bargainDeptName.value = String(row.dept_name ?? '')
  bargainVisible.value = true
  // 仅展示当前点击的这一条产品明细
  bargainItems.value = [{ ...row, dept_name: bargainDeptName.value }]
  fillBargainForm(bargainItems.value[0])
}

async function submitBargain() {
  if (!bargainItemId.value) return
  if (!bargainForm.bargain_meeting_location.trim()) {
    ElMessage.warning('请填写会议地点')
    return
  }
  if (!bargainForm.bargain_meeting_time) {
    ElMessage.warning('请选择会议时间')
    return
  }
  bargainSubmitting.value = true
  try {
    const { data } = await http.post(`/purchase/project/approved-items/${bargainItemId.value}/bargain`, {
      bargain_meeting_location: bargainForm.bargain_meeting_location,
      bargain_meeting_time: bargainForm.bargain_meeting_time,
      bargain_participant_depts: bargainForm.bargain_participant_depts,
      bargain_dept_opinion: bargainForm.bargain_dept_opinion,
      bargain_meeting_content: bargainForm.bargain_meeting_content
    })
    const saved = data.data ?? {}
    // 同步左侧状态
    const idx = bargainItems.value.findIndex((it) => String(it.id) === bargainItemId.value)
    if (idx >= 0) {
      bargainItems.value[idx] = {
        ...bargainItems.value[idx],
        ...saved,
        dept_name: bargainForm.dept_name,
        device_name: bargainForm.device_name
      }
    }
    ElMessage.success('议价记录已保存')
    load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    bargainSubmitting.value = false
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
      const { data } = await http.get('/purchase/project/page', {
        params: {
          page: 1,
          size: 5000,
          keyword: keyword.value || undefined
        }
      })
      list = (data.data?.records ?? []) as Record<string, unknown>[]
    }
    const headers = [
      '订单号',
      '计划单号',
      '年度',
      '申请科室',
      '设备名称',
      '规格型号',
      '预算单价',
      '数量',
      '总价值',
      '提交日期',
      '经费来源',
      '购买用途',
      '申请日期',
      '审核建议',
      '订单审核意见',
      '品牌意向',
      '备注',
      '会议地点',
      '会议时间',
      '参与部门',
      '设备科意见',
      '会议内容'
    ]
    const lines = [headers.join(',')]
    for (const r of list) {
      const fund =
        resolveDictLabel('fund_source', r.fund_source) || String(r.fund_source ?? '')
      const cells = [
        r.order_no,
        r.plan_code,
        r.plan_year,
        r.dept_name,
        r.device_name,
        r.specification,
        r.estimated_price,
        r.quantity,
        r.total_price,
        formatDay(r.submitted_at),
        fund,
        r.purchase_purpose,
        formatDay(r.fill_date),
        r.approval_comment,
        r.order_review_comment,
        r.brand_intent,
        r.plan_remark,
        r.bargain_meeting_location,
        formatDay(r.bargain_meeting_time),
        r.bargain_participant_depts,
        r.bargain_dept_opinion,
        r.bargain_meeting_content
      ].map((v) => {
        const s = v == null ? '' : String(v).replace(/"/g, '""')
        return `"${s}"`
      })
      lines.push(cells.join(','))
    }
    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `purchase_approved_items_export.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success(`已导出 ${list.length} 条`)
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  await loadDict('fund_source')
  load()
})
</script>

<style scoped>
.bargain-split {
  display: grid;
  grid-template-columns: minmax(300px, 34%) minmax(620px, 1fr);
  gap: 20px;
  min-height: 720px;
  align-items: stretch;
}
.bargain-left {
  min-width: 0;
  height: 720px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
}
.bargain-item-table {
  width: 100%;
}
/* 列宽合计超出左侧容器时由表格内部横向滚动 */
.bargain-item-table :deep(.el-table__header-wrapper),
.bargain-item-table :deep(.el-table__body-wrapper) {
  overflow-x: auto !important;
}
.bargain-item-table :deep(.el-scrollbar__wrap) {
  overflow-x: auto !important;
}
.bargain-item-table :deep(.el-table__row) {
  cursor: pointer;
}
.st-todo {
  color: var(--el-color-warning);
}
.st-done {
  color: var(--el-color-success);
}
.bargain-right {
  min-width: 0;
  max-height: 78vh;
  overflow: auto;
  display: flex;
  justify-content: center;
  padding: 4px 8px 8px;
  background: #eceff3;
  border-radius: 4px;
}
.bargain-a4 {
  /* A4 竖版可视宽度（约 210mm） */
  width: min(100%, 794px);
  min-height: 1123px;
  background: #fff;
  border: 1px solid #1a1a1a;
  padding: 28px 32px 36px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  box-sizing: border-box;
}
.bargain-a4__title {
  margin: 8px 0 22px;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #111;
  line-height: 1.4;
}
.bargain-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.bargain-table th,
.bargain-table td {
  border: 1px solid #222;
  padding: 12px 14px;
  vertical-align: middle;
  font-size: 15px;
  line-height: 1.55;
  color: #222;
}
.bargain-table th {
  width: 118px;
  background: #f5f5f5;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
}
.bargain-tall th,
.bargain-tall td {
  vertical-align: top;
}
.bargain-table :deep(.el-input__wrapper),
.bargain-table :deep(.el-textarea__inner) {
  box-shadow: none;
  background: transparent;
}
.bargain-table :deep(.el-textarea__inner) {
  padding: 0;
  min-height: 120px;
}
@media (max-width: 1200px) {
  .bargain-split {
    grid-template-columns: 1fr;
    min-height: auto;
  }
  .bargain-left {
    height: 260px;
  }
  .bargain-a4 {
    min-height: 900px;
  }
}
</style>
