<template>
  <div class="ops-device-page">
    <CrudPage
      ref="crudRef"
      :config="config"
      :hide-add="true"
      hide-operation-column
    >
      <template #toolbar-after-add>
        <el-button type="primary" @click="onBatchGenerate">生成执行</el-button>
      </template>
      <template #extra-columns>
        <el-table-column label="最近计划单" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button
              v-if="row.latest_plan_id"
              link
              type="primary"
              @click="viewPlan({ plan_id: row.latest_plan_id, plan_no: row.latest_plan_no })"
            >
              {{ row.latest_plan_no }}
            </el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="最近执行单" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button
              v-if="row.latest_execution_id"
              link
              type="primary"
              @click="viewExecution({ execution_id: row.latest_execution_id, execution_no: row.latest_execution_no })"
            >
              {{ row.latest_execution_no }}
            </el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="计划单" width="80" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPlans(row)">计划单</el-button>
          </template>
        </el-table-column>
        <el-table-column label="执行单" width="80" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openExecutions(row)">执行单</el-button>
          </template>
        </el-table-column>
        <el-table-column label="生成执行" width="90" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="success" @click="openAdHoc([String(row.id)])">生成</el-button>
          </template>
        </el-table-column>
      </template>
    </CrudPage>

    <AppModal v-model="plansVisible" :title="`${deviceLabel} · 关联计划`" size="xl">
      <el-table :data="plans" border size="small">
        <el-table-column prop="plan_no" label="计划单号" width="150" />
        <el-table-column prop="plan_name" label="计划名称" min-width="140" />
        <el-table-column prop="next_due_date" label="下次到期" width="120" />
        <el-table-column prop="approval_status" label="审核状态" width="100" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewPlan(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="plansVisible = false">关闭</el-button>
      </template>
    </AppModal>

    <AppModal v-model="execsVisible" :title="`${deviceLabel} · 关联执行单`" size="xl">
      <el-table :data="executions" border size="small">
        <el-table-column prop="execution_no" label="执行单号" width="150" />
        <el-table-column prop="source_type" label="来源" width="100" />
        <el-table-column prop="execution_status" label="状态" width="100" />
        <el-table-column prop="planned_date" label="计划日期" width="120" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewExecution(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="execsVisible = false">关闭</el-button>
      </template>
    </AppModal>

    <AppModal v-model="docVisible" :title="docTitle" size="xl">
      <el-descriptions v-if="docHeader" :column="2" border size="small" class="doc-desc">
        <el-descriptions-item v-for="f in docHeaderFields" :key="f.key" :label="f.label">
          {{ docHeader[f.key] ?? '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <FormSection v-if="docItems.length" title="设备明细" class="doc-items">
        <el-table :data="docItems" border size="small" max-height="360">
          <el-table-column prop="device_code" label="设备编码" width="120" />
          <el-table-column prop="device_name" label="设备名称" min-width="140" />
          <el-table-column v-if="docKind === 'plan'" prop="next_due_date" label="下次到期" width="120" />
          <el-table-column v-if="docKind === 'execution'" prop="status" label="状态" width="100" />
          <el-table-column v-if="docKind === 'execution'" prop="overall_result" label="结果" width="100" />
        </el-table>
      </FormSection>
      <template #footer>
        <el-button @click="docVisible = false">关闭</el-button>
      </template>
    </AppModal>

    <AppModal v-model="adHocVisible" title="无计划直开执行单" size="md">
      <p class="ad-hoc-hint">将为 {{ adHocDeviceIds.length }} 台设备创建<strong>一张</strong>执行单（共用模板与类型）。</p>
      <el-form label-width="100px">
        <el-form-item label="模板" required>
          <el-select v-model="adHoc.template_id" filterable style="width: 100%" @change="onTemplateChange">
            <el-option v-for="t in templates" :key="String(t.id)" :label="String(t.template_name)" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="typeLabel" required>
          <el-input v-model="adHoc.typeValue" :placeholder="`填写${typeLabel}`" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="adHoc.planned_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adHocVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdHoc">创建</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import FormSection from '@/components/form/FormSection.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { promptListActionScope, assertScopeSelection } from '@/composables/useListActionScope'

const props = defineProps<{
  module: 'maintain' | 'inspect' | 'pm'
}>()

const titles = { maintain: '保养设备管理', inspect: '巡检设备管理', pm: '预防性维护设备管理' }
const tables = { maintain: 'ops_maintain_device', inspect: 'ops_inspect_device', pm: 'ops_pm_device' }
const typeLabel = computed(() =>
  props.module === 'maintain' ? '保养级别' : props.module === 'inspect' ? '巡检类别' : 'PM类别'
)

type CrudExpose = {
  load: () => void
  selectedCount: number | { value: number }
  selectedIds: () => string[]
  getFilterQueryParams: () => Record<string, string>
}

function readSelectedCount(c: CrudExpose | null): number {
  if (!c) return 0
  const v = c.selectedCount
  return typeof v === 'number' ? v : Number(v?.value ?? 0)
}

const config = computed<PageConfig>(() => ({
  title: titles[props.module],
  apiBase: `/${props.module}`,
  table: tables[props.module],
  listPageUrl: `/${props.module}/device/page`,
  exportUrl: `/${props.module}/device/export`,
  hideAdd: true,
  showRowIndex: true,
  showRowSelection: true,
  listFilters: [
    {
      key: 'device_status',
      label: '设备状态',
      dictType: 'device_status',
      multiple: true,
      actionBar: true,
      dictValues: ['normal', 'in_use', 'maintenance', 'scrap', 'pending_verify']
    },
    {
      key: 'due_within_days',
      label: '到期窗口',
      type: 'select',
      actionBar: true,
      options: [
        { value: '7', label: '7天内' },
        { value: '15', label: '15天内' },
        { value: '30', label: '30天内' },
        { value: '60', label: '60天内' }
      ]
    }
  ],
  moreSearchFields: [
    { key: 'device_code', label: '设备编码', placeholder: '编码模糊' },
    { key: 'device_name', label: '设备名称', placeholder: '名称/简码' },
    { key: 'brand', label: '品牌', placeholder: '品牌模糊' },
    { key: 'specification', label: '规格', placeholder: '规格模糊' },
    { key: 'model', label: '型号', placeholder: '型号模糊' },
    { key: 'serial_number', label: '序列号(SN)', placeholder: '序列号模糊' },
    { key: 'dept_id', label: '科室', placeholder: '科室名称/编码', linkTable: 'department', multiple: true },
    { key: 'manage_dept_id', label: '管理科室', placeholder: '科室名称/编码', linkTable: 'department', multiple: true },
    { key: 'category_id', label: '设备分类', placeholder: '多选分类', linkTable: 'medical_device_category', multiple: true },
    { key: 'category_kw', label: '设备分类模糊', placeholder: '编码/名称' },
    { key: 'asset_category_id', label: '资产分类', placeholder: '多选分类', linkTable: 'asset_category', multiple: true },
    { key: 'asset_category_kw', label: '资产分类模糊', placeholder: '编码/名称' },
    { key: 'finance_category_id', label: '财务分类', placeholder: '多选分类', linkTable: 'finance_category', multiple: true },
    { key: 'finance_category_kw', label: '财务分类模糊', placeholder: '编码/名称' }
  ]
}))

const crudRef = ref<CrudExpose | null>(null)
const device = ref<Record<string, unknown> | null>(null)
const deviceLabel = computed(() => String(device.value?.device_name || device.value?.device_code || '设备'))

const plansVisible = ref(false)
const execsVisible = ref(false)
const plans = ref<Record<string, unknown>[]>([])
const executions = ref<Record<string, unknown>[]>([])

const docVisible = ref(false)
const docKind = ref<'plan' | 'execution'>('plan')
const docTitle = ref('')
const docHeader = ref<Record<string, unknown> | null>(null)
const docItems = ref<Record<string, unknown>[]>([])
const docHeaderFields = computed(() =>
  docKind.value === 'plan'
    ? [
        { key: 'plan_no', label: '计划单号' },
        { key: 'plan_name', label: '计划名称' },
        { key: 'approval_status', label: '审核状态' },
        { key: 'template_name', label: '模板' },
        { key: 'status', label: '状态' },
        { key: 'assigned_user_name', label: '负责人' }
      ]
    : [
        { key: 'execution_no', label: '执行单号' },
        { key: 'status', label: '状态' },
        { key: 'source_type', label: '来源' },
        { key: 'plan_no', label: '计划单号' },
        { key: 'template_name', label: '模板' },
        { key: 'planned_date', label: '计划日期' },
        { key: 'executor_name', label: '执行人' }
      ]
)

const adHocVisible = ref(false)
const adHocDeviceIds = ref<string[]>([])
const templates = ref<Record<string, unknown>[]>([])
const adHoc = ref<Record<string, unknown>>({
  template_id: null,
  typeValue: '',
  planned_date: null
})

async function openPlans(row: Record<string, unknown>) {
  device.value = row
  const { data } = await http.get(`/${props.module}/device/${row.id}/plans`)
  plans.value = data.data ?? []
  plansVisible.value = true
}

async function openExecutions(row: Record<string, unknown>) {
  device.value = row
  const { data } = await http.get(`/${props.module}/device/${row.id}/executions`)
  executions.value = data.data ?? []
  execsVisible.value = true
}

async function viewPlan(row: Record<string, unknown>) {
  const planId = row.plan_id ?? row.id
  if (!planId) return
  const { data } = await http.get(`/${props.module}/plan/${planId}`)
  const doc = data.data ?? {}
  docKind.value = 'plan'
  docTitle.value = `计划单 · ${doc.plan_no || ''}`
  docHeader.value = doc
  docItems.value = (doc.items as Record<string, unknown>[]) || []
  docVisible.value = true
}

async function viewExecution(row: Record<string, unknown>) {
  const execId = row.execution_id ?? row.id
  if (!execId) return
  const { data } = await http.get(`/${props.module}/execution/${execId}`)
  const doc = data.data ?? {}
  docKind.value = 'execution'
  docTitle.value = `执行单 · ${doc.execution_no || ''}`
  docHeader.value = doc
  docItems.value = (doc.items as Record<string, unknown>[]) || []
  docVisible.value = true
}

async function resolveDeviceIds(scope: 'selected' | 'all'): Promise<string[]> {
  if (scope === 'selected') {
    return crudRef.value?.selectedIds() ?? []
  }
  const filters = crudRef.value?.getFilterQueryParams() ?? {}
  const ids: string[] = []
  let page = 1
  const size = 200
  let total = Infinity
  while (ids.length < total) {
    const { data } = await http.get(`/${props.module}/device/page`, {
      params: { ...filters, page, size }
    })
    const pr = data.data ?? {}
    const records = (pr.records ?? []) as Record<string, unknown>[]
    total = Number(pr.total ?? 0)
    for (const r of records) {
      if (r.id != null) ids.push(String(r.id))
    }
    if (records.length === 0) break
    page += 1
    if (page > 500) break
  }
  return ids
}

async function onBatchGenerate() {
  const count = readSelectedCount(crudRef.value)
  const scope = await promptListActionScope(count, '生成执行')
  if (!scope || !assertScopeSelection(scope, count)) return
  const ids = await resolveDeviceIds(scope)
  if (!ids.length) {
    ElMessage.warning('没有可生成的设备')
    return
  }
  await openAdHoc(ids)
}

async function openAdHoc(deviceIds: string[]) {
  adHocDeviceIds.value = deviceIds
  const table =
    props.module === 'maintain'
      ? 'maintenance_template'
      : props.module === 'inspect'
        ? 'inspection_template'
        : 'pm_template'
  const { data } = await http.get(`/${props.module}/${table}/list`)
  templates.value = data.data ?? []
  adHoc.value = { template_id: null, typeValue: '', planned_date: null }
  adHocVisible.value = true
}

function onTemplateChange() {
  const t = templates.value.find((x) => String(x.id) === String(adHoc.value.template_id))
  if (!t) return
  if (props.module === 'maintain') adHoc.value.typeValue = t.maintenance_level ?? ''
  if (props.module === 'inspect') adHoc.value.typeValue = t.inspection_type ?? t.type_name ?? ''
  if (props.module === 'pm') adHoc.value.typeValue = t.pm_type ?? t.type_name ?? ''
}

async function submitAdHoc() {
  if (!adHocDeviceIds.value.length) return
  if (!adHoc.value.template_id) {
    ElMessage.warning('请选择模板')
    return
  }
  if (!adHoc.value.typeValue) {
    ElMessage.warning(`请填写${typeLabel.value}`)
    return
  }
  const body: Record<string, unknown> = {
    template_id: adHoc.value.template_id,
    planned_date: adHoc.value.planned_date,
    client: 'web',
    items: adHocDeviceIds.value.map((device_id) => ({ device_id }))
  }
  if (props.module === 'maintain') body.maintenance_level = adHoc.value.typeValue
  if (props.module === 'inspect') body.inspection_type = adHoc.value.typeValue
  if (props.module === 'pm') body.pm_type = adHoc.value.typeValue
  const { data } = await http.post(`/${props.module}/execution/ad-hoc`, body)
  const execNo = data.data?.execution_no
  ElMessage.success(execNo ? `已创建执行单 ${execNo}` : '已创建执行单')
  adHocVisible.value = false
  crudRef.value?.load()
}
</script>

<style scoped>
.ad-hoc-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.doc-desc {
  margin-bottom: 12px;
}
.doc-items {
  margin-top: 8px;
}
</style>
