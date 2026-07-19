<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail" @saved="onSaved">
      <template #form-header-actions="{ form, mode }">
        <el-button v-if="mode !== 'view'" type="primary" plain @click="openBiddingRef(form)">
          引用招标计划
        </el-button>
      </template>
      <template #form="{ form, mode }">
        <el-form label-width="110px" :disabled="mode === 'view'">
          <FormSection title="基本信息">
            <div class="contract-basic">
              <div class="contract-basic__main">
                <div
                  class="form-grid form-grid--dense"
                  :style="{ gridTemplateColumns: `repeat(${MAIN_COLS}, minmax(0, 1fr))` }"
                >
                  <el-form-item
                    v-for="f in mainFormFields"
                    :key="f.prop"
                    :label="f.label"
                    :required="f.required"
                  >
                    <FieldRenderer v-model="form[f.prop]" :field="f" :model="form" />
                  </el-form-item>
                </div>
              </div>
              <div class="contract-basic__attach">
                <div class="contract-basic__attach-title">证件信息</div>
                <el-form-item
                  v-for="f in attachFormFields"
                  :key="f.prop"
                  :label="f.label"
                  :required="f.required"
                  class="contract-basic__attach-item"
                >
                  <FieldRenderer v-model="form[f.prop]" :field="f" :model="form" />
                </el-form-item>
              </div>
            </div>
          </FormSection>
          <div class="contract-items">
            <div class="contract-items__head">
              <span class="contract-items__title">设备明细</span>
              <el-button v-if="mode !== 'view'" type="primary" link @click="addItem(form)">新增行</el-button>
            </div>
            <el-table :data="ensureItems(form)" border size="small">
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column label="设备名称" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.device_name" placeholder="设备名称" :disabled="mode === 'view'" />
                </template>
              </el-table-column>
              <el-table-column label="设备规格型号" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.specification" placeholder="规格型号" :disabled="mode === 'view'" />
                </template>
              </el-table-column>
              <el-table-column label="品牌" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.brand" placeholder="品牌" :disabled="mode === 'view'" />
                </template>
              </el-table-column>
              <el-table-column label="数量" width="100">
                <template #default="{ row }">
                  <el-input
                    v-model="row.quantity"
                    placeholder="数量"
                    :disabled="mode === 'view'"
                    @change="recalcAmount(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="单价" width="110">
                <template #default="{ row }">
                  <el-input
                    v-model="row.unit_price"
                    placeholder="单价"
                    :disabled="mode === 'view'"
                    @change="recalcAmount(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right">
                <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="生产厂家" min-width="160">
                <template #default="{ row }">
                  <RefSelect
                    v-model="row.manufacturer_id"
                    link-table="manufacturer"
                    placeholder="生产厂家"
                    :disabled="mode === 'view'"
                    @update:model-value="(v) => onManufacturerChange(row, v)"
                  />
                </template>
              </el-table-column>
              <el-table-column v-if="mode !== 'view'" label="操作" width="70" align="center" fixed="right">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeItem(form, $index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="contract-items">
            <div class="contract-items__head">
              <span class="contract-items__title">付款计划</span>
              <el-button v-if="mode !== 'view'" type="primary" link @click="addPaymentRow(form)">
                新增行
              </el-button>
            </div>
            <el-table :data="ensurePayments(form)" border size="small">
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column label="付款日期" width="160">
                <template #default="{ row }">
                  <el-date-picker
                    v-model="row.payment_date"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="付款日期"
                    style="width: 100%"
                    :disabled="mode === 'view'"
                  />
                </template>
              </el-table-column>
              <el-table-column label="支付比例(%)" width="130">
                <template #default="{ row }">
                  <el-input
                    v-model="row.payment_ratio"
                    placeholder="比例"
                    :disabled="mode === 'view'"
                  />
                </template>
              </el-table-column>
              <el-table-column label="付款条件" min-width="180">
                <template #default="{ row }">
                  <el-input
                    v-model="row.payment_condition"
                    placeholder="付款条件"
                    :disabled="mode === 'view'"
                  />
                </template>
              </el-table-column>
              <el-table-column label="付款状态" width="140">
                <template #default="{ row }">
                  <FieldRenderer
                    v-model="row.status"
                    :field="{
                      prop: 'status',
                      label: '付款状态',
                      dictType: 'payment_status',
                      readonly: mode === 'view'
                    }"
                    :model="row"
                  />
                </template>
              </el-table-column>
              <el-table-column v-if="mode !== 'view'" label="操作" width="70" align="center" fixed="right">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removePaymentRow(form, $index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form>
      </template>
      <template #toolbar-after-add>
        <el-button type="primary" @click="reviewSelected">审核</el-button>
      </template>
      <template #toolbar-extra>
        <el-button v-if="contract?.id" type="warning" @click="save">保存合同</el-button>
        <el-button v-if="contract?.id" type="primary" @click="submit">提交审批</el-button>
        <el-button v-if="contract?.id" @click="printContract">打印合同</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="biddingRefVisible" title="引用招标计划" size="xl">
      <el-table
        v-loading="biddingLoading"
        :data="biddingRows"
        border
        size="small"
        max-height="420"
        row-key="id"
        @selection-change="onBiddingSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column type="index" label="序号" width="64" align="center" />
        <el-table-column prop="bidding_no" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="设备规格型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ formatAmount(row.estimated_price) }}</template>
        </el-table-column>
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.total_price) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="biddingRefVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBiddingRef">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="visible" title="采购合同 详情" size="xl">
      <el-tabs v-if="contract">
        <el-tab-pane label="合同信息">
          <FormSection title="基本信息">
            <div class="contract-basic">
              <div class="contract-basic__main">
                <div
                  class="form-grid form-grid--dense"
                  :style="{ gridTemplateColumns: `repeat(${MAIN_COLS}, minmax(0, 1fr))` }"
                >
                  <el-form-item
                    v-for="f in mainFormFields"
                    :key="f.prop"
                    :label="f.label"
                    :required="f.required"
                  >
                    <FieldRenderer v-model="contract[f.prop]" :field="f" :model="contract" />
                  </el-form-item>
                </div>
              </div>
              <div class="contract-basic__attach">
                <div class="contract-basic__attach-title">证件信息</div>
                <el-form-item
                  v-for="f in attachFormFields"
                  :key="f.prop"
                  :label="f.label"
                  :required="f.required"
                  class="contract-basic__attach-item"
                >
                  <FieldRenderer v-model="contract[f.prop]" :field="f" :model="contract" />
                </el-form-item>
              </div>
            </div>
          </FormSection>
          <div class="contract-items mt-12">
            <div class="contract-items__title">设备明细</div>
            <el-table :data="(contract.items as Record<string, unknown>[]) || []" border size="small">
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column prop="device_name" label="设备名称" min-width="140" />
              <el-table-column prop="specification" label="设备规格型号" min-width="140" />
              <el-table-column prop="brand" label="品牌" width="100" />
              <el-table-column prop="quantity" label="数量" width="90" align="right" />
              <el-table-column prop="unit_price" label="单价" width="100" align="right" />
              <el-table-column prop="amount" label="金额" width="100" align="right" />
              <el-table-column prop="manufacturer_name" label="生产厂家" min-width="140" />
            </el-table>
          </div>
        </el-tab-pane>
        <el-tab-pane label="付款明细">
          <el-table :data="payments" border max-height="360">
            <el-table-column v-for="f in paymentFields" :key="f.prop" :label="f.label" :min-width="120">
              <template #default="{ row }">
                <FieldRenderer v-model="row[f.prop]" :field="f" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row, $index }">
                <el-button
                  v-if="row.id && (!row.approval_status || row.approval_status === 'draft')"
                  link
                  type="primary"
                  @click="submitPayment(row)"
                >
                  提交审批
                </el-button>
                <el-button link type="danger" @click="payments.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button class="mt-8" @click="addPayment">添加付款计划</el-button>
        </el-tab-pane>
      </el-tabs>
      <ApprovalPanel
        v-if="contract?.id"
        business-type="purchase_contract"
        :business-id="String(contract.id)"
        @changed="reload"
      />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="warning" @click="save">保存合同</el-button>
        <el-button type="primary" @click="submit">提交审批</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import FormSection from '@/components/form/FormSection.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getDetailFields, getSchema } from '@/config/pageSchemas'
import { printContractDoc } from '@/utils/printDoc'

type ItemRow = {
  device_name: string
  specification: string
  brand: string
  quantity: string | number
  unit_price: string | number
  amount: string | number
  manufacturer_id: string
  manufacturer_name: string
}

type PaymentRow = {
  id?: string
  payment_no?: string
  payment_date: string
  payment_ratio: string | number
  payment_condition: string
  payment_amount?: string | number
  status: string
  approval_status?: string
}

const ATTACH_PROPS = ['registration_cert_url', 'contract_file_url', 'acceptance_report_url'] as const
const MAIN_COLS = 4

const auth = useAuthStore()
const config = getPageConfig('/purchase/contract')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const contract = ref<Record<string, unknown> | null>(null)
const payments = ref<Record<string, unknown>[]>([])
const paymentFields = getDetailFields('contract_payment')
const manufacturerCache = ref<Map<string, string>>(new Map())

const biddingRefVisible = ref(false)
const biddingLoading = ref(false)
const biddingRows = ref<Record<string, unknown>[]>([])
const biddingSelected = ref<Record<string, unknown>[]>([])
const biddingTargetForm = ref<Record<string, unknown> | null>(null)

const contractSchemaFields = computed(() => getSchema(config.table).filter((f) => f.form !== false))
const attachPropSet = new Set<string>(ATTACH_PROPS)
const mainFormFields = computed(() => contractSchemaFields.value.filter((f) => !attachPropSet.has(f.prop)))
const attachFormFields = computed(() =>
  ATTACH_PROPS.map((prop) => contractSchemaFields.value.find((f) => f.prop === prop)).filter(
    (f): f is NonNullable<typeof f> => !!f
  )
)

async function openBiddingRef(form: Record<string, unknown>) {
  biddingTargetForm.value = form
  biddingSelected.value = []
  biddingRefVisible.value = true
  biddingLoading.value = true
  try {
    const { data } = await http.get('/purchase/bidding/page', {
      params: { page: 1, size: 500, bidding_review_result: 'passed' }
    })
    biddingRows.value = data.data?.records ?? []
  } catch {
    biddingRows.value = []
    ElMessage.error('加载招标计划失败')
  } finally {
    biddingLoading.value = false
  }
}

function onBiddingSelectionChange(rows: Record<string, unknown>[]) {
  biddingSelected.value = rows
}

function confirmBiddingRef() {
  const form = biddingTargetForm.value
  if (!form) return
  if (!biddingSelected.value.length) {
    ElMessage.warning('请至少选择一条招标明细')
    return
  }
  form.items = biddingSelected.value.map((row) => {
    const quantity = row.quantity ?? ''
    const unitPrice = row.estimated_price ?? ''
    let amount: string | number = row.total_price ?? ''
    if ((amount === '' || amount == null) && quantity !== '' && unitPrice !== '') {
      const q = Number(quantity)
      const p = Number(unitPrice)
      if (Number.isFinite(q) && Number.isFinite(p)) {
        amount = Math.round(q * p * 100) / 100
      }
    }
    return {
      device_name: String(row.device_name ?? ''),
      specification: String(row.specification ?? ''),
      brand: '',
      quantity,
      unit_price: unitPrice,
      amount,
      manufacturer_id: '',
      manufacturer_name: ''
    } satisfies ItemRow
  })
  biddingRefVisible.value = false
  ElMessage.success(`已引用 ${biddingSelected.value.length} 条招标明细`)
}

function emptyItem(): ItemRow {
  return {
    device_name: '',
    specification: '',
    brand: '',
    quantity: '',
    unit_price: '',
    amount: '',
    manufacturer_id: '',
    manufacturer_name: ''
  }
}

function emptyPayment(): PaymentRow {
  return {
    payment_date: '',
    payment_ratio: '',
    payment_condition: '',
    status: 'pending',
    approval_status: 'draft'
  }
}

function ensureItems(form: Record<string, unknown>) {
  if (!Array.isArray(form.items)) {
    form.items = [emptyItem()]
  }
  return form.items as ItemRow[]
}

function ensurePayments(form: Record<string, unknown>) {
  if (!Array.isArray(form.payments)) {
    form.payments = [emptyPayment()]
  }
  return form.payments as PaymentRow[]
}

function addItem(form: Record<string, unknown>) {
  ensureItems(form).push(emptyItem())
}

function removeItem(form: Record<string, unknown>, index: number) {
  const list = ensureItems(form)
  list.splice(index, 1)
  if (!list.length) list.push(emptyItem())
}

function addPaymentRow(form: Record<string, unknown>) {
  ensurePayments(form).push(emptyPayment())
}

function removePaymentRow(form: Record<string, unknown>, index: number) {
  const list = ensurePayments(form)
  list.splice(index, 1)
  if (!list.length) list.push(emptyPayment())
}

function recalcAmount(row: ItemRow) {
  const q = Number(row.quantity)
  const p = Number(row.unit_price)
  if (!Number.isFinite(q) || !Number.isFinite(p)) {
    row.amount = ''
    return
  }
  row.amount = Math.round(q * p * 100) / 100
}

function formatAmount(v: unknown) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : String(v)
}

async function ensureManufacturerCache() {
  if (manufacturerCache.value.size) return
  const { data } = await http.get('/system/manufacturer/list', { params: { limit: 500 } })
  const list = (data.data?.records ?? data.data ?? []) as Record<string, unknown>[]
  const map = new Map<string, string>()
  for (const r of list) {
    if (r.id != null) map.set(String(r.id), String(r.manufacturer_name ?? ''))
  }
  manufacturerCache.value = map
}

async function onManufacturerChange(row: ItemRow, v: unknown) {
  await ensureManufacturerCache()
  const id = v == null || v === '' ? '' : String(v)
  row.manufacturer_id = id
  row.manufacturer_name = id ? manufacturerCache.value.get(id) || '' : ''
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/purchase/contract/${row.id}`)
  contract.value = data.data
  payments.value = (data.data?.payments as Record<string, unknown>[]) ?? []
  visible.value = true
}

function onSaved() {
  // list reloads inside CrudPage
}

function isContractApproved(row: Record<string, unknown>) {
  return String(row.approval_status ?? '') === 'approved'
}

async function reviewSelected() {
  const selected = crudRef.value?.getSelectedRows?.() ?? []
  if (!selected.length) {
    ElMessage.warning('请先勾选要审核的合同')
    return
  }
  const pending = selected.filter((r) => !isContractApproved(r))
  if (!pending.length) {
    ElMessage.warning('勾选的合同均已审批，无需重复审核')
    return
  }
  if (pending.length < selected.length) {
    ElMessage.info(`已跳过 ${selected.length - pending.length} 条已审批合同`)
  }
  try {
    await ElMessageBox.confirm(`确认审核通过选中的 ${pending.length} 份合同？`, '合同审核', {
      type: 'warning',
      confirmButtonText: '确认审核'
    })
  } catch {
    return
  }
  try {
    const { data } = await http.post('/purchase/contract/review', {
      ids: pending.map((r) => r.id)
    })
    if (data?.code != null && data.code !== 0) {
      ElMessage.error(data.message || '审核失败')
      return
    }
    const approved = data?.data?.approved ?? pending.length
    ElMessage.success(`已审核 ${approved} 条`)
    crudRef.value?.load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } }; message?: string }
    ElMessage.error(err?.response?.data?.message || err?.message || '审核失败')
  }
}

function addPayment() {
  payments.value.push({
    payment_no: 'PAY' + Date.now(),
    payment_stage: 'advance',
    payment_amount: 0,
    status: 'pending',
    approval_status: 'draft'
  })
}

async function save() {
  if (!contract.value) return
  await http.post('/purchase/contract', {
    ...contract.value,
    payments: payments.value,
    items: contract.value.items ?? []
  })
  visible.value = false
  crudRef.value?.load()
}

async function submit() {
  if (!contract.value?.id) return
  await http.post(`/purchase/contract/${contract.value.id}/submit`, { applicantId: auth.user?.userId })
  ElMessage.success('已提交合同审批')
  await reload()
}

async function submitPayment(row: Record<string, unknown>) {
  if (!contract.value?.id || !row.id) return
  await http.post('/purchase/contract', {
    ...contract.value,
    payments: payments.value,
    items: contract.value.items ?? []
  })
  await http.post(`/purchase/contract/${contract.value.id}/payments/${row.id}/submit`, {
    applicantId: auth.user?.userId
  })
  ElMessage.success('已提交付款审批')
  await reload()
}

async function reload() {
  if (!contract.value?.id) return
  const { data } = await http.get(`/purchase/contract/${contract.value.id}`)
  contract.value = data.data
  payments.value = (data.data?.payments as Record<string, unknown>[]) ?? []
}

function printContract() {
  if (!contract.value) return
  printContractDoc(contract.value)
}
</script>

<style scoped>
.contract-basic {
  display: flex;
  gap: 16px;
  align-items: stretch;
}
.contract-basic__main {
  flex: 1;
  min-width: 0;
}
.contract-basic__attach {
  flex: 0 0 360px;
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank, #fff);
  box-sizing: border-box;
}
.contract-basic__attach-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.contract-basic__attach-item {
  margin-bottom: 0;
}
.contract-basic__attach :deep(.el-form-item) {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  margin-bottom: 8px;
}
.contract-basic__attach :deep(.el-form-item__label) {
  width: 80px !important;
  min-width: 80px;
  max-width: 80px;
  text-align: right;
  line-height: 32px;
  padding: 0 6px 0 0;
  height: auto !important;
  white-space: nowrap;
  flex-shrink: 0;
  font-size: 12px;
}
.contract-basic__attach :deep(.el-form-item__content) {
  margin-left: 0 !important;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}
.contract-basic__attach :deep(.file-upload-field) {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}
.contract-basic__attach :deep(.file-upload-field .el-input) {
  flex: 0 0 110px;
  width: 110px;
  max-width: 110px;
}
.contract-basic__attach :deep(.file-upload-field .el-upload),
.contract-basic__attach :deep(.file-upload-field .el-link) {
  flex-shrink: 0;
  white-space: nowrap;
}
.form-grid {
  display: grid;
  gap: 4px 16px;
}
.form-grid--dense :deep(.el-form-item) {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 10px;
}
.form-grid--dense :deep(.el-form-item__label) {
  width: auto !important;
  min-width: 72px;
  padding: 0 6px 0 0;
  line-height: 1.25;
  white-space: nowrap;
  text-align: right;
  font-size: 12px;
  height: auto !important;
  flex-shrink: 0;
}
.form-grid--dense :deep(.el-form-item__content) {
  margin-left: 0 !important;
  flex: 1;
  min-width: 0;
}
.form-grid :deep(.el-select),
.form-grid :deep(.el-input),
.form-grid :deep(.el-input-number) {
  width: 100%;
}
.contract-items {
  margin-top: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 12px;
}
.contract-items__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.contract-items__title {
  font-weight: 600;
}
.mt-8 {
  margin-top: 8px;
}
.mt-12 {
  margin-top: 12px;
}
</style>
