<template>
  <div class="purchase-approval-page">
    <SystemPageCard title="采购审批" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="单号 / 院区 / 科室" @search="onSearch" @reset="onReset">
          <template #filters>
            <el-select v-model="businessType" placeholder="业务类型" clearable class="filter-item" @change="onSearch">
              <el-option label="采购计划" value="purchase_plan" />
              <el-option label="采购项目" value="purchase_project" />
              <el-option label="采购合同" value="purchase_contract" />
              <el-option label="安装验收" value="purchase_acceptance" />
              <el-option label="合同付款" value="contract_payment" />
            </el-select>
            <el-select
              v-model="status"
              placeholder="状态"
              clearable
              multiple
              collapse-tags
              collapse-tags-tooltip
              class="filter-item"
              @change="onSearch"
            >
              <el-option label="待审批" value="pending" />
              <el-option label="已通过" value="approved" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
          </template>
          <template #actions>
            <el-button type="primary" @click="openActDialog('approve')">审核</el-button>
            <el-button type="danger" plain @click="openActDialog('reject')">驳回</el-button>
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
        @row-dblclick="openDetail"
      >
        <el-table-column type="selection" width="48" fixed="left" reserve-selection />
        <el-table-column type="index" label="序号" width="64" align="center" :index="rowSerial" />
        <el-table-column prop="plan_code" label="计划单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="campus_name" label="院区" min-width="100" show-overflow-tooltip />
        <el-table-column prop="dept_name" label="科室名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="submitted_at" label="提交日期" width="120">
          <template #default="{ row }">{{ formatDay(row.submitted_at ?? row.created_at) }}</template>
        </el-table-column>
        <el-table-column prop="applicant_name" label="提交人" width="100" show-overflow-tooltip />
        <el-table-column prop="total_budget" label="总金额(预算/元)" min-width="140" align="right">
          <template #default="{ row }">
            <TableCellValue :field="budgetField" :value="row.total_budget" />
          </template>
        </el-table-column>
        <el-table-column prop="plan_year" label="计划年度" width="100" />
        <el-table-column prop="plan_type" label="计划类型" width="110">
          <template #default="{ row }">
            <TableCellValue :field="planTypeField" :value="row.plan_type" />
          </template>
        </el-table-column>
        <el-table-column prop="approved_by_name" label="审核人" width="100" />
        <el-table-column prop="approved_at" label="审核日期" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDay(row.approved_at) }}</template>
        </el-table-column>
        <el-table-column prop="approval_status" label="审批状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.approval_status ?? row.status" dict-type="approval_status" />
          </template>
        </el-table-column>
        <el-table-column prop="approval_comment" label="审核建议" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.approval_comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="benefit_analysis_url" label="效益分析" width="140">
          <template #default="{ row }">
            <TableFileCell
              :value="row.benefit_analysis_url"
              prop="benefit_analysis_url"
              :row-id="planRowId(row)"
              save-base="/purchase/plan"
              @updated="(url) => onFileUpdated(row, 'benefit_analysis_url', url)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="dept_argument_url" label="科室论证纪要" width="150">
          <template #default="{ row }">
            <TableFileCell
              :value="row.dept_argument_url"
              prop="dept_argument_url"
              :row-id="planRowId(row)"
              save-base="/purchase/plan"
              @updated="(url) => onFileUpdated(row, 'dept_argument_url', url)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="140"
          fixed="right"
          align="center"
          header-align="center"
          class-name="col-operations"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
              <el-button link type="primary" @click.stop="openProgress(row)">进度</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </SystemPageCard>

    <ApprovalProgressDrawer
      v-model="progressVisible"
      :business-type="progressBusinessType"
      :business-id="progressBusinessId"
      :title="progressTitle"
    />

    <AppModal v-model="actVisible" :title="actMode === 'approve' ? '审核' : '驳回'" size="sm">
      <el-form label-width="88px">
        <el-form-item label="审核建议" required>
          <el-input
            v-model="actComment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请填写审核建议"
          />
        </el-form-item>
        <div class="act-hint">将处理已勾选且状态为「审批中」的 {{ pendingSelectedCount }} 条单据</div>
      </el-form>
      <template #footer>
        <el-button @click="actVisible = false">取消</el-button>
        <el-button
          :type="actMode === 'approve' ? 'primary' : 'danger'"
          :loading="actSubmitting"
          @click="submitAct"
        >
          确认{{ actMode === 'approve' ? '审核' : '驳回' }}
        </el-button>
      </template>
    </AppModal>

    <AppModal v-model="visible" title="采购申请 编辑" size="xl">
      <div v-loading="planLoading">
        <template v-if="planMaster">
          <GroupedFormFields
            table="purchase_plan"
            :model="planMaster"
            :fields="viewBasicFields"
            :group-columns="{ basic: 6 }"
            :highlight-labels="['plan_type', 'campus_id', 'dept_id', 'total_budget']"
          />
          <MasterDetailForm
            :items="planItems"
            :show-add-button="false"
            :show-operations="false"
          >
            <template #detail-columns>
              <el-table-column
                v-for="f in viewDetailFields"
                :key="f.prop"
                :prop="f.prop"
                :label="f.label"
                :width="f.width"
                :min-width="f.width ?? Math.max(120, f.label.length * 14 + 24)"
                :sortable="f.detailSortable || false"
                show-overflow-tooltip
              >
                <template #header>
                  <span :class="{ 'detail-col-required': f.required }">{{ f.label }}</span>
                </template>
                <template #default="{ row }">
                  <TableCellValue :field="f" :value="row[f.prop]" />
                </template>
              </el-table-column>
            </template>
          </MasterDetailForm>
        </template>
        <el-empty v-else-if="!planLoading" description="暂无单据数据" :image-size="64" />
      </div>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import AppModal from '@/components/AppModal.vue'
import ApprovalProgressDrawer from '@/components/ApprovalProgressDrawer.vue'
import MasterDetailForm from '@/components/MasterDetailForm.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import StatusTag from '@/components/table/StatusTag.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import TableFileCell from '@/components/table/TableFileCell.vue'
import { getDetailFields, getSchema, type FieldSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope, assertScopeSelection } from '@/composables/useListActionScope'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { loadDict, resolveDictLabel } = useDict()
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const businessType = ref('purchase_plan')
const status = ref<string[]>([])
const visible = ref(false)
const tableRef = ref()

const {
  selectedCount,
  syncFromTable,
  selectedIds,
  clearAll
} = useCrossPageSelection()

const selectedRowMap = ref(new Map<string, Record<string, unknown>>())

const planLoading = ref(false)
const planMaster = ref<Record<string, unknown> | null>(null)
const planItems = ref<Record<string, unknown>[]>([])

const masterFormFields = computed(() =>
  getSchema('purchase_plan').filter((f) => {
    if (f.form === false) return false
    if (f.readonly && f.form !== true) return false
    return true
  })
)
const viewBasicFields = computed(() =>
  masterFormFields.value
    .filter((f) => (f.group ?? 'other') === 'basic')
    .map((f) => ({ ...f, readonly: true }))
)
const viewDetailFields = computed(() =>
  getDetailFields('purchase_plan_item').map((f) => ({ ...f, readonly: true }))
)

const progressVisible = ref(false)
const progressBusinessType = ref('purchase_plan')
const progressBusinessId = ref('')
const progressTitle = ref('审批进度')

const actVisible = ref(false)
const actMode = ref<'approve' | 'reject'>('approve')
const actComment = ref('同意审批')
const actSubmitting = ref(false)

const budgetField: FieldSchema = { prop: 'total_budget', label: '总金额(预算/元)', type: 'number' }
const planTypeField: FieldSchema = { prop: 'plan_type', label: '计划类型', dictType: 'plan_type' }

const pendingSelectedCount = computed(() =>
  selectedIds().filter((id) => {
    const row = selectedRowMap.value.get(id)
    return isPending(row)
  }).length
)

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

function planRowId(row: Record<string, unknown>) {
  if (String(row.business_type) !== 'purchase_plan') return ''
  return row.business_id != null ? String(row.business_id) : ''
}

function onFileUpdated(row: Record<string, unknown>, prop: string, url: string) {
  row[prop] = url
}

function isPending(row: Record<string, unknown> | undefined) {
  if (!row) return false
  const s = String(row.approval_status ?? row.status ?? '')
  return s === 'pending'
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
    const { data } = await http.get('/purchase/approval/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        businessType: businessType.value || undefined,
        status: status.value.length ? status.value.join(',') : undefined
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
  businessType.value = 'purchase_plan'
  status.value = []
  onSearch()
}

function openActDialog(mode: 'approve' | 'reject') {
  if (selectedCount.value === 0) {
    ElMessage.warning('请先勾选要处理的单据')
    return
  }
  if (pendingSelectedCount.value === 0) {
    ElMessage.warning('勾选行中没有「审批中」的单据')
    return
  }
  actMode.value = mode
  actComment.value = mode === 'approve' ? '同意审批' : '驳回'
  actVisible.value = true
}

async function submitAct() {
  const comment = actComment.value.trim()
  if (!comment) {
    ElMessage.warning('请填写审核建议')
    return
  }
  const approverId = auth.user?.userId
  if (!approverId) {
    ElMessage.error('未登录或无法获取当前用户')
    return
  }
  const targets = selectedIds()
    .map((id) => selectedRowMap.value.get(id))
    .filter((row): row is Record<string, unknown> => !!row && isPending(row) && row.id != null)

  if (!targets.length) {
    ElMessage.warning('没有可处理的审批中单据')
    return
  }

  actSubmitting.value = true
  let ok = 0
  let fail = 0
  try {
    const path = actMode.value === 'approve' ? 'approve' : 'reject'
    for (const row of targets) {
      try {
        await http.post(`/system/approval/${row.id}/${path}`, {
          approverId,
          comment
        })
        ok++
      } catch {
        fail++
      }
    }
    if (ok && !fail) {
      ElMessage.success(actMode.value === 'approve' ? `已审核通过 ${ok} 条` : `已驳回 ${ok} 条`)
    } else if (ok && fail) {
      ElMessage.warning(`成功 ${ok} 条，失败 ${fail} 条`)
    } else {
      ElMessage.error('操作失败')
    }
    actVisible.value = false
    clearAll(tableRef.value)
    selectedRowMap.value = new Map()
    await load()
  } finally {
    actSubmitting.value = false
  }
}

async function fetchExportRows() {
  const { data } = await http.get('/purchase/approval/page', {
    params: {
      page: 1,
      size: 5000,
      keyword: keyword.value || undefined,
      businessType: businessType.value || undefined,
      status: status.value.length ? status.value.join(',') : undefined
    }
  })
  return (data.data?.records ?? []) as Record<string, unknown>[]
}

async function exportCsv() {
  try {
    const scope = await promptListActionScope(selectedCount.value, '导出')
    if (!scope) return
    if (!assertScopeSelection(scope, selectedCount.value)) return

    let list = await fetchExportRows()
    if (scope === 'selected') {
      const ids = new Set(selectedIds())
      list = list.filter((r) => r.id != null && ids.has(String(r.id)))
    }

    const headers = [
      '计划单号',
      '院区',
      '科室名称',
      '提交日期',
      '提交人',
      '总金额(预算/元)',
      '计划年度',
      '计划类型',
      '审核人',
      '审核日期',
      '审批状态',
      '审核建议'
    ]
    const lines = [headers.join(',')]
    for (const r of list) {
      const statusLabel =
        resolveDictLabel('approval_status', r.approval_status ?? r.status) ||
        String(r.approval_status ?? r.status ?? '')
      const planType =
        resolveDictLabel('plan_type', r.plan_type) || String(r.plan_type ?? '')
      const cells = [
        r.plan_code ?? r.business_no,
        r.campus_name,
        r.dept_name,
        formatDay(r.submitted_at ?? r.created_at),
        r.applicant_name,
        r.total_budget,
        r.plan_year,
        planType,
        r.approved_by_name,
        formatDay(r.approved_at),
        statusLabel,
        r.approval_comment
      ].map((v) => {
        const s = v == null ? '' : String(v).replace(/"/g, '""')
        return `"${s}"`
      })
      lines.push(cells.join(','))
    }
    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `purchase_approval_export.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success(`已导出 ${list.length} 条`)
  } catch {
    ElMessage.error('导出失败')
  }
}

async function openDetail(row: Record<string, unknown>) {
  if (String(row.business_type) !== 'purchase_plan' || !row.business_id) {
    ElMessage.warning('当前仅支持查看采购计划类单据')
    return
  }
  visible.value = true
  planLoading.value = true
  planMaster.value = null
  planItems.value = []
  try {
    const { data } = await http.get(`/purchase/plan/${row.business_id}`)
    if (data.code === 0 && data.data) {
      const { items, ...master } = data.data as Record<string, unknown> & { items?: Record<string, unknown>[] }
      planMaster.value = master
      planItems.value = items ?? []
    } else {
      ElMessage.error(data.message || '加载单据失败')
      visible.value = false
    }
  } catch {
    ElMessage.error('加载单据失败')
    visible.value = false
  } finally {
    planLoading.value = false
  }
}

function openProgress(row: Record<string, unknown>) {
  progressBusinessType.value = String(row.business_type ?? 'purchase_plan')
  progressBusinessId.value = String(row.business_id ?? '')
  const code = row.plan_code != null ? String(row.plan_code) : String(row.business_no ?? '')
  progressTitle.value = code ? `审批进度 · ${code}` : '审批进度'
  progressVisible.value = true
}

watch(visible, (v) => {
  if (!v) {
    planMaster.value = null
    planItems.value = []
  }
})

onMounted(async () => {
  await loadDict('approval_status')
  await loadDict('plan_type')
  load()
})
</script>

<style scoped>
.filter-item { width: 160px; }
.act-hint {
  margin-left: 88px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
}
:deep(.col-operations) {
  background: var(--el-bg-color);
}
.detail-col-required::before {
  content: '*';
  color: var(--el-color-danger);
  margin-right: 2px;
}
</style>
