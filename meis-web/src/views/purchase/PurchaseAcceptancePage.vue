<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail" @saved="onSaved">
      <template #form-header-actions="{ form, mode }">
        <el-button v-if="mode !== 'view'" type="primary" plain @click="openContractRef(form)">
          引入合同
        </el-button>
      </template>
      <template #actions-after>
        <el-button type="primary" @click="reviewSelected">审核</el-button>
      </template>
      <template #form="{ form, mode }">
        <el-form label-width="110px" :disabled="mode === 'view'">
          <FormSection title="基本信息">
            <div class="acceptance-basic">
              <div class="acceptance-basic__main">
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
              <div class="acceptance-basic__attach">
                <div class="acceptance-basic__attach-title">附件信息</div>
                <el-form-item
                  v-for="f in attachFormFields"
                  :key="f.prop"
                  :label="f.label"
                  :required="f.required"
                  class="acceptance-basic__attach-item"
                >
                  <FieldRenderer v-model="form[f.prop]" :field="f" :model="form" />
                </el-form-item>
              </div>
            </div>
          </FormSection>
          <div class="acceptance-items">
            <div class="acceptance-items__head">
              <span class="acceptance-items__title">设备明细</span>
              <el-button v-if="mode !== 'view'" type="primary" link @click="addDevice(form)">新增行</el-button>
            </div>
            <el-table :data="ensureDevices(form)" border size="small">
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
                  <el-button link type="danger" @click="removeDevice(form, $index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="acceptance-items">
            <div class="acceptance-items__head">
              <span class="acceptance-items__title">验收参数</span>
            </div>
            <el-table :data="ensureMembers(form)" border size="small">
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column label="项目" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">{{ row.member_name || '-' }}</template>
              </el-table-column>
              <el-table-column label="验收内容" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">{{ row.acceptance_content || '-' }}</template>
              </el-table-column>
              <el-table-column label="验收结果" width="140">
                <template #default="{ row }">
                  <el-input v-model="row.acceptance_result" placeholder="验收结果" :disabled="mode === 'view'" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.remark" placeholder="备注" :disabled="mode === 'view'" />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form>
      </template>
      <template #toolbar-extra>
        <el-button v-if="acceptance?.id" type="primary" @click="submitApproval">提交验收审批</el-button>
        <el-button
          v-if="acceptance?.id && acceptance.approval_status === 'approved' && !acceptance.entry_id"
          type="success"
          @click="passAcceptance"
        >
          验收通过并生成入库单
        </el-button>
        <el-button v-if="acceptance?.entry_id" type="primary" @click="goEntry">查看入库单</el-button>
        <el-button v-if="acceptance?.id" @click="printDoc">打印验收单</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="contractRefVisible" title="引入合同" size="xl">
      <el-table
        v-loading="contractRefLoading"
        :data="contractRefRows"
        border
        size="small"
        max-height="420"
        row-key="id"
        @selection-change="onContractRefSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column type="index" label="序号" width="64" align="center" />
        <el-table-column prop="contract_code" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="设备规格型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ formatAmount(row.unit_price) }}</template>
        </el-table-column>
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="contractRefVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmContractRef">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="visible" title="安装验收 详情" size="xl">
      <template v-if="acceptance">
        <el-tabs>
          <el-tab-pane label="验收信息">
            <GroupedFormFields :table="config.table" :model="acceptance" />
            <div class="acceptance-items mt-12">
              <div class="acceptance-items__title">设备明细</div>
              <el-table :data="(acceptance.devices as Record<string, unknown>[]) || []" border size="small">
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
          <el-tab-pane label="验收清单">
            <el-table :data="checkItems" border size="small" max-height="320">
              <el-table-column prop="item_name" label="检查项目" min-width="140" />
              <el-table-column prop="check_standard" label="验收标准" min-width="180" />
              <el-table-column label="结果" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.check_result" size="small">
                    <el-option label="待检" value="pending" />
                    <el-option label="合格" value="passed" />
                    <el-option label="不合格" value="failed" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="通过" width="70">
                <template #default="{ row }">
                  <el-switch v-model="row.is_passed" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.remark" size="small" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="验收参数">
            <el-table :data="members" border size="small" max-height="280">
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column prop="member_name" label="项目" min-width="100" show-overflow-tooltip />
              <el-table-column prop="acceptance_content" label="验收内容" min-width="260" show-overflow-tooltip />
              <el-table-column label="验收结果" width="140">
                <template #default="{ row }">
                  <el-input v-model="row.acceptance_result" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.remark" size="small" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
        <ApprovalPanel
          v-if="acceptance.id"
          business-type="purchase_acceptance"
          :business-id="String(acceptance.id)"
          @changed="reload"
        />
        <el-alert
          v-if="acceptance.entry_no"
          :title="`已关联入库单：${acceptance.entry_no}（${acceptance.entry_status ?? ''}）`"
          type="success"
          show-icon
          class="entry-alert"
        />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="saveDetail">保存</el-button>
        <el-button type="warning" @click="submitApproval">提交验收审批</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import FormSection from '@/components/form/FormSection.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'
import { printAcceptanceDoc } from '@/utils/printDoc'

type DeviceRow = {
  device_name: string
  specification: string
  brand: string
  quantity: string | number
  unit_price: string | number
  amount: string | number
  manufacturer_id: string
  manufacturer_name: string
}

type MemberRow = {
  member_role: string
  member_name: string
  acceptance_content: string
  acceptance_result: string
  remark: string
}

const ATTACH_PROPS = ['quality_check_report_url', 'installation_report_url', 'report_url'] as const
const MAIN_COLS = 4

const auth = useAuthStore()
const router = useRouter()
const config = getPageConfig('/purchase/acceptance')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const acceptance = ref<Record<string, unknown> | null>(null)
const checkItems = ref<Record<string, unknown>[]>([])
const members = ref<Record<string, unknown>[]>([])
const manufacturerCache = ref<Map<string, string>>(new Map())

const contractRefVisible = ref(false)
const contractRefLoading = ref(false)
const contractRefRows = ref<Record<string, unknown>[]>([])
const contractRefSelected = ref<Record<string, unknown>[]>([])
const contractRefTargetForm = ref<Record<string, unknown> | null>(null)

const schemaFields = computed(() => getSchema(config.table).filter((f) => f.form !== false))
const attachPropSet = new Set<string>(ATTACH_PROPS)
const mainFormFields = computed(() => schemaFields.value.filter((f) => !attachPropSet.has(f.prop)))
const attachFormFields = computed(() =>
  ATTACH_PROPS.map((prop) => schemaFields.value.find((f) => f.prop === prop)).filter(
    (f): f is NonNullable<typeof f> => !!f
  )
)

async function openContractRef(form: Record<string, unknown>) {
  contractRefTargetForm.value = form
  contractRefSelected.value = []
  contractRefVisible.value = true
  contractRefLoading.value = true
  try {
    const { data } = await http.get('/purchase/contract/ref-items', {
      params: { page: 1, size: 500 }
    })
    contractRefRows.value = data.data?.records ?? []
  } catch {
    contractRefRows.value = []
    ElMessage.error('加载合同明细失败')
  } finally {
    contractRefLoading.value = false
  }
}

function onContractRefSelectionChange(rows: Record<string, unknown>[]) {
  contractRefSelected.value = rows
}

function confirmContractRef() {
  const form = contractRefTargetForm.value
  if (!form) return
  if (!contractRefSelected.value.length) {
    ElMessage.warning('请至少选择一条合同明细')
    return
  }
  form.devices = contractRefSelected.value.map((row) => {
    const quantity = row.quantity ?? ''
    const unitPrice = row.unit_price ?? ''
    let amount: string | number = row.amount ?? ''
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
      brand: String(row.brand ?? ''),
      quantity,
      unit_price: unitPrice,
      amount,
      manufacturer_id: row.manufacturer_id != null ? String(row.manufacturer_id) : '',
      manufacturer_name: String(row.manufacturer_name ?? '')
    } satisfies DeviceRow
  })
  const first = contractRefSelected.value[0]
  if (first?.contract_id != null) form.contract_id = first.contract_id
  if (first?.supplier_id != null) form.supplier_id = first.supplier_id
  contractRefVisible.value = false
  ElMessage.success(`已引入 ${contractRefSelected.value.length} 条合同明细`)
}

const DEFAULT_PARAMS: MemberRow[] = [
  {
    member_role: '1',
    member_name: '开箱前',
    acceptance_content: '箱体是否有损坏、破坏、碰撞等说明',
    acceptance_result: '',
    remark: ''
  },
  {
    member_role: '2',
    member_name: '开箱后',
    acceptance_content: '技术资料（含说明书、合格证、图纸、保修卡、装箱清单）',
    acceptance_result: '',
    remark: ''
  },
  {
    member_role: '3',
    member_name: '开箱后',
    acceptance_content: '清点物件（物件的齐全或缺漏情况描述）',
    acceptance_result: '',
    remark: ''
  }
]

function emptyDevice(): DeviceRow {
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

function ensureDevices(form: Record<string, unknown>) {
  if (!Array.isArray(form.devices)) {
    form.devices = [emptyDevice()]
  }
  return form.devices as DeviceRow[]
}

function ensureMembers(form: Record<string, unknown>) {
  if (!Array.isArray(form.members) || form.members.length === 0) {
    form.members = DEFAULT_PARAMS.map((r) => ({ ...r }))
  }
  return form.members as MemberRow[]
}

function addDevice(form: Record<string, unknown>) {
  ensureDevices(form).push(emptyDevice())
}

function removeDevice(form: Record<string, unknown>, index: number) {
  const list = ensureDevices(form)
  list.splice(index, 1)
  if (!list.length) list.push(emptyDevice())
}

function recalcAmount(row: DeviceRow) {
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

async function onManufacturerChange(row: DeviceRow, v: unknown) {
  await ensureManufacturerCache()
  const id = v == null || v === '' ? '' : String(v)
  row.manufacturer_id = id
  row.manufacturer_name = id ? manufacturerCache.value.get(id) || '' : ''
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/purchase/acceptance/${row.id}`)
  acceptance.value = data.data
  checkItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  members.value = (data.data?.members as Record<string, unknown>[]) ?? []
  visible.value = true
}

function onSaved() {
  // list reloads inside CrudPage
}

function isAcceptancePassed(row: Record<string, unknown>) {
  return String(row.approval_status ?? '') === 'approved'
}

async function reviewSelected() {
  const selected = crudRef.value?.getSelectedRows?.() ?? []
  if (!selected.length) {
    ElMessage.warning('请先勾选要审核的验收单')
    return
  }
  const pending = selected.filter((r) => !isAcceptancePassed(r))
  if (!pending.length) {
    ElMessage.warning('勾选的验收单均已审核，无需重复审核')
    return
  }
  if (pending.length < selected.length) {
    ElMessage.info(`已跳过 ${selected.length - pending.length} 条已审核记录`)
  }
  try {
    await ElMessageBox.confirm(`确认审核通过选中的 ${pending.length} 条验收单？`, '验收审核', {
      type: 'warning',
      confirmButtonText: '确认审核'
    })
  } catch {
    return
  }
  try {
    const { data } = await http.post('/purchase/acceptance/review', {
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

async function saveDetail() {
  if (!acceptance.value) return
  await http.post('/purchase/acceptance', {
    ...acceptance.value,
    items: checkItems.value,
    members: members.value,
    devices: acceptance.value.devices ?? []
  })
  ElMessage.success('已保存')
  visible.value = false
  crudRef.value?.load()
}

async function submitApproval() {
  if (!acceptance.value?.id) return
  await http.post('/purchase/acceptance', {
    ...acceptance.value,
    items: checkItems.value,
    members: members.value,
    devices: acceptance.value.devices ?? []
  })
  try {
    await http.post(`/purchase/acceptance/${acceptance.value.id}/submit`, { applicantId: auth.user?.userId })
    ElMessage.success('已提交验收审批')
    await reload()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '提交失败')
  }
}

async function passAcceptance() {
  if (!acceptance.value?.id) return
  const { data } = await http.post(`/purchase/acceptance/${acceptance.value.id}/pass`)
  ElMessage.success(`已生成入库单 ${data.data?.entry_no ?? ''}`)
  acceptance.value = data.data
  crudRef.value?.load()
}

function goEntry() {
  if (!acceptance.value?.entry_id) return
  router.push('/asset/entry')
}

async function reload() {
  if (!acceptance.value?.id) return
  const { data } = await http.get(`/purchase/acceptance/${acceptance.value.id}`)
  acceptance.value = data.data
  checkItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  members.value = (data.data?.members as Record<string, unknown>[]) ?? []
  crudRef.value?.load()
}

function printDoc() {
  if (!acceptance.value) return
  printAcceptanceDoc({
    ...acceptance.value,
    items: checkItems.value,
    members: members.value
  })
}
</script>

<style scoped>
.acceptance-basic {
  display: flex;
  gap: 16px;
  align-items: stretch;
}
.acceptance-basic__main {
  flex: 1;
  min-width: 0;
}
.acceptance-basic__attach {
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
.acceptance-basic__attach-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.acceptance-basic__attach-item {
  margin-bottom: 0;
}
.acceptance-basic__attach :deep(.el-form-item) {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  margin-bottom: 8px;
}
.acceptance-basic__attach :deep(.el-form-item__label) {
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
.acceptance-basic__attach :deep(.el-form-item__content) {
  margin-left: 0 !important;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}
.acceptance-basic__attach :deep(.file-upload-field) {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}
.acceptance-basic__attach :deep(.file-upload-field .el-input) {
  flex: 0 0 110px;
  width: 110px;
  max-width: 110px;
}
.acceptance-basic__attach :deep(.file-upload-field .el-upload),
.acceptance-basic__attach :deep(.file-upload-field .el-link) {
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
.acceptance-items {
  margin-top: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 12px;
}
.acceptance-items__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.acceptance-items__title {
  font-weight: 600;
}
.entry-alert {
  margin-top: 12px;
}
.mt-12 {
  margin-top: 12px;
}
</style>
