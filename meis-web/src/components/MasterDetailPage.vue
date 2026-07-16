<template>
  <div class="master-detail-page">
    <CrudPage ref="crudRef" :config="config" detail-mode @detail="loadDetail" @add="openCreate">
      <template #toolbar-extra>
        <el-button v-if="selectedId" type="warning" @click="saveMaster">保存主从</el-button>
        <el-button v-if="selectedId && showApproval && canSubmit" type="primary" @click="submitApproval">提交审批</el-button>
        <el-button v-if="selectedId && showApproval && canWithdraw" @click="withdrawApproval">撤回审批</el-button>
        <slot name="toolbar-extra" :master="master" :reload="reloadMaster" />
      </template>
    </CrudPage>

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
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <FieldRenderer v-if="f.linkTable || f.dictType || f.type === 'boolean'" v-model="row[f.prop]" :field="f" />
                <el-input v-else-if="f.type === 'textarea'" v-model="row[f.prop]" type="textarea" :rows="2" />
                <el-input-number
                  v-else-if="f.type === 'number'"
                  v-model="row[f.prop]"
                  :min="0"
                  :controls="false"
                  style="width:100%"
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
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from './CrudPage.vue'
import MasterDetailForm from './MasterDetailForm.vue'
import FieldRenderer from './FieldRenderer.vue'
import AppModal from './AppModal.vue'
import GroupedFormFields from './form/GroupedFormFields.vue'
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

function defaultItem() {
  const item: Record<string, unknown> = {}
  for (const f of detailFields.value) {
    item[f.prop] = f.type === 'number' ? 0 : f.type === 'boolean' ? false : ''
  }
  return item
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
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
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
  await http.post(props.saveUrl, { ...master.value, items: items.value })
  detailVisible.value = false
  crudRef.value?.load()
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
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
  await loadApprovalState()
}

defineExpose({ selectedId })
</script>
