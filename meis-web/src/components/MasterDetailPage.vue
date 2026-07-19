<template>
  <div class="master-detail-page">
    <CrudPage
      ref="crudRef"
      :config="config"
      detail-mode
      :can-edit="canEditRow"
      :can-delete="canDeleteRow"
      @detail="loadDetail"
      @add="openCreate"
    >
      <template #actions-after>
        <el-button v-if="showApproval" type="primary" @click="submitApproval">提交</el-button>
        <el-button v-if="showApproval" @click="withdrawApproval">撤回审批</el-button>
        <slot
          name="actions-after"
          :master="master"
          :reload="reloadList"
          :selected-id="selectedId"
          :get-selected-rows="getSelectedRows"
        />
      </template>
      <template #toolbar-extra>
        <slot name="toolbar-extra" :master="master" :reload="reloadList" :get-selected-rows="getSelectedRows" />
      </template>
      <template v-if="showApproval" #row-actions-before="{ row }">
        <el-button
          v-if="showProgressAction(row)"
          link
          type="primary"
          @click.stop="openProgress(row)"
        >
          进度
        </el-button>
      </template>
      <template v-if="$slots['row-actions']" #row-actions="{ row }">
        <slot name="row-actions" :row="row" />
      </template>
    </CrudPage>

    <ApprovalProgressDrawer
      v-if="showApproval"
      v-model="progressVisible"
      :business-type="props.businessType!"
      :business-id="progressBusinessId"
      :title="progressTitle"
    />

    <AppModal v-model="detailVisible" :title="config.title + ' 编辑'" size="xl">
      <template #header-actions>
        <slot
          name="detail-header-actions"
          :master="master"
          :items="items"
          :replace-items="replaceItems"
          :visible="detailVisible"
        />
      </template>
      <template v-if="master">
        <GroupedFormFields
          :table="config.table"
          :model="master"
          :fields="basicFormFields"
          :group-columns="config.formGroupColumns"
          :highlight-labels="['plan_type', 'campus_id', 'dept_id', 'total_budget']"
        />
        <MasterDetailForm :items="items" :show-add-button="false" @add-item="addItem">
          <template #detail-columns>
            <el-table-column
              v-for="f in detailFields"
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
                <WarehouseStockDeviceSelect
                  v-if="isGoodsReturnStockField(f.prop)"
                  :model-value="String(row[f.prop] ?? '')"
                  :warehouse-id="goodsReturnWarehouseId"
                  :mode="f.prop === 'device_name' ? 'name' : 'code'"
                  :exclude-ids="goodsReturnExcludeIds"
                  @update:model-value="(v) => (row[f.prop] = v)"
                  @select="(device) => applyGoodsReturnStock(row, device)"
                  @clear="() => clearGoodsReturnStock(row)"
                />
                <FieldRenderer
                  v-else-if="f.linkTable || f.dictType || f.type === 'boolean' || f.type === 'date' || f.type === 'datetime' || f.type === 'file'"
                  v-model="row[f.prop]"
                  :field="f"
                  @update:model-value="() => onDetailFieldChange(row, f.prop)"
                />
                <el-input v-else-if="f.type === 'textarea'" v-model="row[f.prop]" type="textarea" :rows="2" />
                <el-input-number
                  v-else-if="f.type === 'number'"
                  v-model="row[f.prop]"
                  :min="numberMin(f)"
                  :controls="false"
                  :disabled="f.readonly"
                  style="width:100%"
                  @change="() => onDetailFieldChange(row, f.prop)"
                />
                <el-input v-else v-model="row[f.prop]" :disabled="f.readonly" />
              </template>
            </el-table-column>
          </template>
        </MasterDetailForm>
        <GroupedFormFields
          v-if="extraFormFields.length"
          :table="config.table"
          :model="master"
          :fields="extraFormFields"
        />
      </template>
      <template #footer>
        <el-button type="primary" plain @click="addItem">添加明细</el-button>
        <el-button @click="detailVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMaster">保存</el-button>
        <el-button v-if="canApprove" type="success" @click="approvePlan">通过</el-button>
        <el-button v-if="canApprove" type="danger" @click="rejectPlan">驳回</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FieldSchema } from '@/config/pageSchemas'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from './CrudPage.vue'
import MasterDetailForm from './MasterDetailForm.vue'
import FieldRenderer from './FieldRenderer.vue'
import AppModal from './AppModal.vue'
import GroupedFormFields from './form/GroupedFormFields.vue'
import WarehouseStockDeviceSelect from './form/WarehouseStockDeviceSelect.vue'
import ApprovalProgressDrawer from './ApprovalProgressDrawer.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getDetailFields, getSchema } from '@/config/pageSchemas'

const props = defineProps<{
  config: PageConfig
  saveUrl: string
  businessType?: string
}>()

const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const detailVisible = ref(false)
const master = ref<Record<string, unknown> | null>(null)
const items = ref<Record<string, unknown>[]>([])
const selectedId = ref('')
const approvalInstanceId = ref('')
const progressVisible = ref(false)
const progressBusinessId = ref('')
const progressTitle = ref('审批进度')
const showApproval = computed(() => !!props.businessType)
const detailFields = computed(() => getDetailFields(props.config.detailTable ?? `${props.config.table}_item`))
const masterFormFields = computed(() =>
  getSchema(props.config.table).filter((f) => {
    if (f.form === false) return false
    // 默认只读冗余字段不进表单；显式 form:true 的只读字段仍展示（如总金额汇总）
    if (f.readonly && f.form !== true) return false
    return true
  })
)
const basicFormFields = computed(() => masterFormFields.value.filter((f) => (f.group ?? 'other') === 'basic'))
const extraFormFields = computed(() =>
  masterFormFields.value.filter((f) => {
    const g = f.group ?? 'other'
    return g !== 'basic' && g !== 'approval'
  })
)
const canApprove = computed(
  () => !!selectedId.value && !!props.businessType && !!approvalInstanceId.value && master.value?.approval_status === 'pending'
)

const isGoodsReturn = computed(() => props.config.table === 'device_goods_return')
const goodsReturnWarehouseId = computed(() => {
  const v = master.value?.warehouse_id
  return v != null && String(v).trim() !== '' ? String(v) : ''
})
const goodsReturnExcludeIds = computed(() =>
  items.value.map((it) => String(it.device_id ?? '')).filter((id) => id && id !== 'undefined')
)

function isGoodsReturnStockField(prop: string) {
  return isGoodsReturn.value && (prop === 'device_code' || prop === 'device_name')
}

function toPrice(v: unknown): number | null {
  const n = toNumber(v)
  return n
}

function applyGoodsReturnStock(row: Record<string, unknown>, device: Record<string, unknown>) {
  row.device_id = device.id != null ? String(device.id) : null
  row.device_code = device.device_code ?? ''
  row.device_name = device.device_name ?? ''
  row.specification = device.specification ?? device.model ?? ''
  row.unit = device.unit_name ?? device.unit ?? ''
  if (row.quantity == null || row.quantity === '' || Number(row.quantity) <= 0) {
    row.quantity = 1
  }
  const price = toPrice(device.original_value) ?? toPrice(device.contract_price)
  if (price != null) row.unit_price = price
  row.manufacturer_id = device.manufacturer_id ?? null
  row.serial_number = device.serial_number ?? ''
  row.brand = device.brand ?? ''
  row.category_id = device.category_id ?? null
  row.category_name = device.category_name ?? ''
  row.asset_category_id = device.asset_category_id ?? null
  row.asset_category_name = device.asset_category_name ?? ''
  row.finance_category_id = device.finance_category_id ?? null
  row.finance_category_name = device.finance_category_name ?? ''
  recalcLineAmount(row)
  syncTotalBudget()
}

function clearGoodsReturnStock(row: Record<string, unknown>) {
  row.device_id = null
  row.device_code = ''
  row.device_name = ''
  row.specification = ''
  row.unit = ''
  row.unit_price = null
  row.total_price = null
  row.manufacturer_id = null
  row.serial_number = ''
  row.brand = ''
  row.category_id = null
  row.category_name = ''
  row.asset_category_id = null
  row.asset_category_name = ''
  row.finance_category_id = null
  row.finance_category_name = ''
  syncTotalBudget()
}

function approvalStatusOf(row: Record<string, unknown> | null | undefined) {
  return String(row?.approval_status ?? 'draft')
}

function canEditRow(row: Record<string, unknown>) {
  const st = String(row.status ?? '')
  if (st === 'completed' || st === 'issued' || st === 'returned') return false
  const s = approvalStatusOf(row)
  return s === 'draft' || s === 'rejected'
}

function canDeleteRow(row: Record<string, unknown>) {
  return canEditRow(row)
}

/** 未提交不显示进度；已提交（审批中/已通过/已驳回）显示 */
function showProgressAction(row: Record<string, unknown>) {
  return approvalStatusOf(row) !== 'draft'
}

function resolveTargetRow(): Record<string, unknown> | null {
  if (selectedId.value && master.value?.id && String(master.value.id) === selectedId.value) {
    return master.value
  }
  const selected = crudRef.value?.getSelectedRows?.() ?? []
  if (selected.length === 1) return selected[0]
  if (selected.length > 1) {
    ElMessage.warning('请只勾选一条单据')
    return null
  }
  if (selectedId.value) {
    return { id: selectedId.value, approval_status: master.value?.approval_status }
  }
  return null
}

function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function applyCurrentUserDefaults(target: Record<string, unknown>) {
  if (!target.applicant_id && auth.user?.userId) {
    target.applicant_id = auth.user.userId
    target.applicant_name = auth.user.realName ?? ''
  }
  if (!target.fill_date) {
    target.fill_date = todayStr()
  }
}

function toNumber(v: unknown): number | null {
  if (v == null || v === '') return null
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : null
}

function numberMin(f: FieldSchema): number {
  if (f.minExclusive == null) return 0
  // 数量等整数：大于 0 → 控件最小为 1
  return Number.isInteger(f.minExclusive) ? f.minExclusive + 1 : f.minExclusive
}

function recalcLineAmount(row: Record<string, unknown>) {
  const qty = toNumber(row.quantity)
  const price = toNumber(row.unit_price ?? row.estimated_price)
  if (qty == null || price == null) {
    row.total_price = null
    return
  }
  row.total_price = Math.round(qty * price * 100) / 100
}

/** 主表总金额 = 明细金额合计（只读，不手工录入） */
function syncTotalBudget() {
  if (!master.value) return
  let sum = 0
  for (const row of items.value) {
    const name = row.device_name
    if (name == null || String(name).trim() === '') continue
    recalcLineAmount(row)
    const line = toNumber(row.total_price)
    if (line != null) sum += line
  }
  master.value.total_budget = Math.round(sum * 100) / 100
}

function onDetailFieldChange(row: Record<string, unknown>, prop: string) {
  if (prop === 'quantity' || prop === 'estimated_price' || prop === 'unit_price') {
    recalcLineAmount(row)
    syncTotalBudget()
  }
}

function defaultItem() {
  const item: Record<string, unknown> = {}
  for (const f of detailFields.value) {
    if (f.prop === 'quantity') {
      item.quantity = 1
    } else if (f.prop === 'total_price') {
      item.total_price = null
    } else if (f.type === 'number') {
      item[f.prop] = null
    } else if (f.type === 'boolean') {
      item[f.prop] = false
    } else {
      item[f.prop] = ''
    }
  }
  return item
}

function isBlank(v: unknown) {
  return v == null || String(v).trim() === ''
}

function validateDetailItems(rows: Record<string, unknown>[]): string | null {
  if (!rows.length) return null
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    const line = i + 1
    for (const f of detailFields.value) {
      if (!f.required) continue
      const v = row[f.prop]
      if (f.type === 'number') {
        const n = toNumber(v)
        if (n == null) return `第 ${line} 行请填写${f.label}`
        if (f.minExclusive != null && !(n > f.minExclusive)) {
          return `第 ${line} 行${f.label}须大于 ${f.minExclusive}`
        }
      } else if (isBlank(v)) {
        return `第 ${line} 行请填写${f.label}`
      }
    }
  }
  return null
}

async function loadApprovalState() {
  approvalInstanceId.value = ''
  if (!selectedId.value || !props.businessType) return
  const { data } = await http.get('/system/approval/business', {
    params: { businessType: props.businessType, businessId: selectedId.value }
  })
  if (data.data?.id && data.data?.status === 'pending') {
    approvalInstanceId.value = String(data.data.id)
  }
}

async function loadDetail(row: Record<string, unknown>) {
  selectedId.value = String(row.id)
  const { data } = await http.get(`${props.saveUrl}/${row.id}`)
  const plan = (data.data ?? {}) as Record<string, unknown>
  applyCurrentUserDefaults(plan)
  master.value = plan
  items.value = ((data.data?.items as Record<string, unknown>[]) ?? []).map((it) => {
    const rowItem = { ...it }
    recalcLineAmount(rowItem)
    return rowItem
  })
  syncTotalBudget()
  detailVisible.value = true
  await loadApprovalState()
}

async function openCreate() {
  selectedId.value = ''
  approvalInstanceId.value = ''
  const defaults: Record<string, unknown> = { approval_status: 'draft', is_active: true }
  for (const f of masterFormFields.value) {
    if (defaults[f.prop] !== undefined) continue
    defaults[f.prop] = f.type === 'number' ? undefined : f.type === 'boolean' ? false : ''
  }
  applyCurrentUserDefaults(defaults)
  if (props.config.table === 'purchase_plan') {
    // 计划年度默认当前自然年；计划编号由后端按 CG-日期+流水生成
    defaults.plan_year = new Date().getFullYear()
    try {
      const { data } = await http.get(`${props.saveUrl}/next-code`)
      if (data.data?.plan_code) defaults.plan_code = data.data.plan_code
      if (data.data?.plan_year != null) defaults.plan_year = data.data.plan_year
    } catch {
      // 编号接口失败时仍可打开表单，保存时后端会兜底生成
    }
  }
  if (props.config.table === 'device_entry') {
    defaults.entry_date = todayStr()
    defaults.entry_type = 'purchase'
    defaults.status = 'draft'
    defaults.approval_status = 'draft'
    try {
      const { data } = await http.get(`${props.saveUrl}/next-no`)
      if (data.data?.entry_no) defaults.entry_no = data.data.entry_no
    } catch {
      // 编号接口失败时仍可打开表单，保存时后端会兜底生成
    }
    try {
      const { data } = await http.get('/system/warehouse/list', { params: { limit: 50 } })
      const rows = (data.data?.records ?? data.data ?? []) as Record<string, unknown>[]
      const first = rows.find((r) => r.is_active !== false && r.id != null)
      if (first?.id != null) defaults.warehouse_id = String(first.id)
    } catch {
      // 仓库列表失败时仍可打开表单，保存时必填校验会提示
    }
  }
  if (props.config.table === 'device_goods_return') {
    defaults.return_date = todayStr()
    defaults.created_at = todayStr()
    defaults.status = 'draft'
    defaults.doc_status = 'draft'
    defaults.approval_status = 'draft'
    if (auth.user?.realName) defaults.created_by_name = auth.user.realName
    else if (auth.user?.username) defaults.created_by_name = auth.user.username
    try {
      const { data } = await http.get('/system/warehouse/list', { params: { limit: 50 } })
      const rows = (data.data?.records ?? data.data ?? []) as Record<string, unknown>[]
      const first = rows.find((r) => r.is_active !== false && r.id != null)
      if (first?.id != null) defaults.warehouse_id = String(first.id)
    } catch {
      // 仓库列表失败时仍可打开表单
    }
  }
  master.value = defaults
  items.value = []
  syncTotalBudget()
  detailVisible.value = true
}

function addItem() {
  if (isGoodsReturn.value && !goodsReturnWarehouseId.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  items.value.push(defaultItem())
  syncTotalBudget()
}

function replaceItems(rows: Record<string, unknown>[]) {
  items.value = rows.map((row) => {
    const base = defaultItem()
    return { ...base, ...row }
  })
  syncTotalBudget()
}

watch(
  items,
  () => {
    syncTotalBudget()
  },
  { deep: true }
)

function openProgress(row: Record<string, unknown>) {
  progressBusinessId.value = String(row.id ?? '')
  const code = row.plan_code != null ? String(row.plan_code) : ''
  progressTitle.value = code ? `审批进度 · ${code}` : '审批进度'
  progressVisible.value = true
}

async function saveMaster() {
  if (!master.value) return
  applyCurrentUserDefaults(master.value)
  const missing = basicFormFields.value.filter((f) => {
    if (!f.required) return false
    if (f.prop === 'total_budget') return false // 由明细自动汇总
    const v = master.value![f.prop]
    return v === null || v === undefined || v === ''
  })
  if (missing.length) {
    ElMessage.warning(`请填写：${missing.map((f) => f.label).join('、')}`)
    return
  }
  if (props.config.table === 'device_entry') {
    const wh = master.value.warehouse_id
    if (wh == null || String(wh).trim() === '') {
      ElMessage.warning('请选择仓库')
      return
    }
  }
  if (isGoodsReturn.value) {
    const wh = master.value.warehouse_id
    if (wh == null || String(wh).trim() === '') {
      ElMessage.warning('请选择仓库')
      return
    }
  }
  const payloadItems = items.value.filter((it) => {
    const name = it.device_name
    return name != null && String(name).trim() !== ''
  })
  if (items.value.length > 0 && payloadItems.length === 0) {
    ElMessage.warning('请填写明细资产名称，或删除空白明细行')
    return
  }
  if (isGoodsReturn.value) {
    const missingDevice = payloadItems.find((it) => !it.device_id)
    if (missingDevice) {
      ElMessage.warning('请从库存中选择资产（输入资产编码或名称后点选）')
      return
    }
  }
  const detailErr = validateDetailItems(payloadItems)
  if (detailErr) {
    ElMessage.warning(detailErr)
    return
  }
  for (const row of payloadItems) recalcLineAmount(row)
  syncTotalBudget()
  const payload: Record<string, unknown> = {
    ...master.value,
    items: payloadItems
  }
  // 计划年度仅采购计划需要；勿套用到入库/出库等其它主从单据
  if (props.config.table === 'purchase_plan') {
    const planYear = master.value.plan_year
    const yearNum =
      typeof planYear === 'number'
        ? planYear
        : planYear != null && String(planYear).trim() !== ''
          ? Number(planYear)
          : undefined
    if (yearNum == null || Number.isNaN(yearNum)) {
      ElMessage.warning('请填写计划年度')
      return
    }
    payload.plan_year = yearNum
  }
  try {
    await http.post(props.saveUrl, payload)
    ElMessage.success('保存成功')
    detailVisible.value = false
    crudRef.value?.load()
  } catch {
    // 错误提示由 http 拦截器统一处理
  }
}

async function submitApproval() {
  const row = resolveTargetRow()
  if (!row?.id) {
    ElMessage.warning('请先勾选一条单据')
    return
  }
  const status = approvalStatusOf(row)
  if (status !== 'draft' && status !== 'rejected') {
    ElMessage.warning(status === 'pending' ? '该单据已在审批中' : '该单据不可再提交')
    return
  }
  const id = String(row.id)
  await http.post(`${props.saveUrl}/${id}/submit`, { applicantId: auth.user?.userId })
  ElMessage.success('已提交审批')
  selectedId.value = id
  await reloadMaster()
  crudRef.value?.load()
}

async function withdrawApproval() {
  const row = resolveTargetRow()
  if (!row?.id) {
    ElMessage.warning('请先勾选一条单据')
    return
  }
  if (approvalStatusOf(row) !== 'pending') {
    ElMessage.warning('仅审批中的单据可撤回')
    return
  }
  const id = String(row.id)
  await http.post(`${props.saveUrl}/${id}/withdraw`, { applicantId: auth.user?.userId })
  ElMessage.success('已撤回审批')
  selectedId.value = id
  await reloadMaster()
  crudRef.value?.load()
}

async function approvePlan() {
  if (!approvalInstanceId.value) return
  await http.post(`/system/approval/${approvalInstanceId.value}/approve`, {
    approverId: auth.user?.userId,
    comment: '同意'
  })
  ElMessage.success('已通过')
  await reloadMaster()
  crudRef.value?.load()
}

async function rejectPlan() {
  if (!approvalInstanceId.value) return
  await http.post(`/system/approval/${approvalInstanceId.value}/reject`, {
    approverId: auth.user?.userId,
    comment: '驳回'
  })
  ElMessage.success('已驳回')
  await reloadMaster()
  crudRef.value?.load()
}

async function reloadMaster() {
  if (!selectedId.value) return
  const { data } = await http.get(`${props.saveUrl}/${selectedId.value}`)
  const plan = (data.data ?? {}) as Record<string, unknown>
  applyCurrentUserDefaults(plan)
  master.value = plan
  items.value = ((data.data?.items as Record<string, unknown>[]) ?? []).map((it) => {
    const rowItem = { ...it }
    recalcLineAmount(rowItem)
    return rowItem
  })
  syncTotalBudget()
  await loadApprovalState()
}

function getSelectedRows(): Record<string, unknown>[] {
  return crudRef.value?.getSelectedRows?.() ?? []
}

function reloadList() {
  crudRef.value?.load()
  void reloadMaster()
}

defineExpose({ selectedId, resolveTargetRow, getSelectedRows })
</script>

<style scoped>
.detail-col-required::before {
  content: '*';
  color: var(--el-color-danger);
  margin-right: 4px;
}
</style>
