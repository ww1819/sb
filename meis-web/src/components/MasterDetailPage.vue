<template>
  <div class="master-detail-page">
    <CrudPage ref="crudRef" :config="config" detail-mode @detail="loadDetail" @add="openCreate">
      <template #toolbar-extra>
        <el-button v-if="selectedId" type="warning" @click="saveMaster">保存主从</el-button>
        <el-button v-if="selectedId && showApproval && canSubmit" type="primary" @click="submitApproval">提交审批</el-button>
        <el-button v-if="selectedId && showApproval && canWithdraw" @click="withdrawApproval">撤回审批</el-button>
        <slot name="toolbar-extra" :master="master" :reload="reloadMaster" />
      </template>
      <template v-if="showApproval" #row-actions-before="{ row }">
        <el-button link type="primary" @click.stop="openProgress(row)">进度</el-button>
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
                <FieldRenderer
                  v-if="f.linkTable || f.dictType || f.type === 'boolean'"
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
                <el-input v-else v-model="row[f.prop]" />
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
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FieldSchema } from '@/config/pageSchemas'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from './CrudPage.vue'
import MasterDetailForm from './MasterDetailForm.vue'
import FieldRenderer from './FieldRenderer.vue'
import AppModal from './AppModal.vue'
import GroupedFormFields from './form/GroupedFormFields.vue'
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
  getSchema(props.config.table).filter((f) => !f.readonly && f.form !== false)
)
const basicFormFields = computed(() => masterFormFields.value.filter((f) => (f.group ?? 'other') === 'basic'))
const extraFormFields = computed(() =>
  masterFormFields.value.filter((f) => {
    const g = f.group ?? 'other'
    return g !== 'basic' && g !== 'approval'
  })
)
const canSubmit = computed(() => {
  const status = master.value?.approval_status
  return !status || status === 'draft' || status === 'rejected'
})
const canWithdraw = computed(() => master.value?.approval_status === 'pending')
const canApprove = computed(
  () => !!selectedId.value && !!props.businessType && !!approvalInstanceId.value && master.value?.approval_status === 'pending'
)

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
  const price = toNumber(row.estimated_price)
  if (qty == null || price == null) {
    row.total_price = null
    return
  }
  row.total_price = Math.round(qty * price * 100) / 100
}

function onDetailFieldChange(row: Record<string, unknown>, prop: string) {
  if (prop === 'quantity' || prop === 'estimated_price') {
    recalcLineAmount(row)
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
  detailVisible.value = true
  await loadApprovalState()
}

function openCreate() {
  selectedId.value = ''
  approvalInstanceId.value = ''
  const defaults: Record<string, unknown> = { approval_status: 'draft', is_active: true }
  for (const f of masterFormFields.value) {
    if (defaults[f.prop] !== undefined) continue
    defaults[f.prop] = f.type === 'number' ? undefined : f.type === 'boolean' ? false : ''
  }
  applyCurrentUserDefaults(defaults)
  master.value = defaults
  items.value = []
  detailVisible.value = true
}

function addItem() {
  items.value.push(defaultItem())
}

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
    const v = master.value![f.prop]
    return v === null || v === undefined || v === ''
  })
  if (missing.length) {
    ElMessage.warning(`请填写：${missing.map((f) => f.label).join('、')}`)
    return
  }
  const payloadItems = items.value.filter((it) => {
    const name = it.device_name
    return name != null && String(name).trim() !== ''
  })
  if (items.value.length > 0 && payloadItems.length === 0) {
    ElMessage.warning('请填写明细设备名称，或删除空白明细行')
    return
  }
  const detailErr = validateDetailItems(payloadItems)
  if (detailErr) {
    ElMessage.warning(detailErr)
    return
  }
  for (const row of payloadItems) recalcLineAmount(row)
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
  try {
    await http.post(props.saveUrl, {
      ...master.value,
      plan_year: yearNum,
      items: payloadItems
    })
    ElMessage.success('保存成功')
    detailVisible.value = false
    crudRef.value?.load()
  } catch {
    // 错误提示由 http 拦截器统一处理
  }
}

async function submitApproval() {
  if (!selectedId.value) return
  await http.post(`${props.saveUrl}/${selectedId.value}/submit`, { applicantId: auth.user?.userId })
  ElMessage.success('已提交审批')
  await reloadMaster()
  crudRef.value?.load()
}

async function withdrawApproval() {
  if (!selectedId.value) return
  await http.post(`${props.saveUrl}/${selectedId.value}/withdraw`, { applicantId: auth.user?.userId })
  ElMessage.success('已撤回审批')
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
  await loadApprovalState()
}

defineExpose({ selectedId })
</script>

<style scoped>
.detail-col-required::before {
  content: '*';
  color: var(--el-color-danger);
  margin-right: 4px;
}
</style>
