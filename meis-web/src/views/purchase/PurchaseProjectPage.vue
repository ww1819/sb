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
            <el-button @click="exportCsv">导出</el-button>
          </template>
        </PageFilterBar>
      </template>

      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id">
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
        <el-table-column label="操作" width="88" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReview(row)">审核</el-button>
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>

    <AppModal v-model="reviewVisible" title="订单审核" size="sm">
      <el-form label-width="110px">
        <el-form-item label="订单号">
          <span>{{ reviewRow?.order_no || '-' }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import AppModal from '@/components/AppModal.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'

const { loadDict, resolveDictLabel } = useDict()
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')

const reviewVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewRow = ref<Record<string, unknown> | null>(null)
const reviewComment = ref('同意审核')

const priceField: FieldSchema = { prop: 'estimated_price', label: '预算单价', type: 'number' }
const qtyField: FieldSchema = { prop: 'quantity', label: '数量', type: 'number' }
const totalField: FieldSchema = { prop: 'total_price', label: '总价值', type: 'number' }
const fundField: FieldSchema = { prop: 'fund_source', label: '经费来源', dictType: 'fund_source' }

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
  load()
}

function onReset() {
  keyword.value = ''
  onSearch()
}

function openReview(row: Record<string, unknown>) {
  reviewRow.value = row
  reviewComment.value = String(row.order_review_comment || '同意审核')
  reviewVisible.value = true
}

async function submitReview() {
  const comment = reviewComment.value.trim()
  if (!comment) {
    ElMessage.warning('请填写订单审核意见')
    return
  }
  if (!reviewRow.value?.id) return
  reviewSubmitting.value = true
  try {
    await http.post(`/purchase/project/approved-items/${reviewRow.value.id}/order-review`, { comment })
    ElMessage.success('已保存订单审核意见')
    reviewVisible.value = false
    load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '审核失败')
  } finally {
    reviewSubmitting.value = false
  }
}

async function exportCsv() {
  try {
    const { data } = await http.get('/purchase/project/page', {
      params: {
        page: 1,
        size: 5000,
        keyword: keyword.value || undefined
      }
    })
    const list = (data.data?.records ?? []) as Record<string, unknown>[]
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
      '备注'
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
        r.plan_remark
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
