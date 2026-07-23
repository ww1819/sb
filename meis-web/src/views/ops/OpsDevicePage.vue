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
        <el-button @click="onBatchInclude">申请纳入计划</el-button>
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
        <el-table-column label="申请纳入" width="90" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openInclude([String(row.id)])">纳入</el-button>
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
          {{
            f.key.endsWith('_channel')
              ? channelLabel(docHeader[f.key])
              : (docHeader[f.key] ?? '—')
          }}
        </el-descriptions-item>
      </el-descriptions>
      <FormSection v-if="docItems.length" title="设备明细" class="doc-items">
        <el-table :data="docItems" border size="small" max-height="360">
          <el-table-column prop="device_code" label="设备编码" width="120" />
          <el-table-column prop="device_name" label="设备名称" min-width="140" />
          <el-table-column v-if="docKind === 'plan'" prop="next_due_date" label="下次到期" width="120" />
          <el-table-column v-if="docKind === 'execution'" prop="status" label="状态" width="100" />
          <el-table-column v-if="docKind === 'execution'" prop="overall_result" label="结果" width="100" />
          <el-table-column v-if="docKind === 'execution'" label="执行途径" width="90">
            <template #default="{ row }">{{ channelLabel(row.execution_channel) }}</template>
          </el-table-column>
          <el-table-column v-if="docKind === 'execution'" label="确认途径" width="90">
            <template #default="{ row }">{{ channelLabel(row.confirm_channel) }}</template>
          </el-table-column>
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
          <el-select v-model="adHoc.typeId" filterable clearable style="width: 100%" placeholder="请选择">
            <el-option
              v-for="o in typeOptions"
              :key="String(o.id)"
              :label="typeOptionLabel(o)"
              :value="String(o.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="周期类型" required>
          <el-select v-model="adHoc.cycle_type" style="width: 100%" @change="syncAdHocCycleDays">
            <el-option label="天" value="day" />
            <el-option label="周" value="week" />
            <el-option label="月" value="month" />
            <el-option label="年" value="year" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期值" required>
          <el-input-number v-model="adHoc.cycle_value" :min="1" style="width: 100%" @change="syncAdHocCycleDays" />
        </el-form-item>
        <el-form-item label="周期(天)">
          <el-input :model-value="adHoc.cycle_days ?? ''" disabled />
        </el-form-item>
        <el-form-item label="执行日期" required>
          <el-date-picker
            v-model="adHoc.planned_date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            @change="onAdHocDateChange"
          />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="adHoc.execute_start_time"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="adHoc.execute_end_time"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adHocVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdHoc">创建</el-button>
      </template>
    </AppModal>
    <AppModal v-model="includeVisible" title="申请纳入计划" size="xl">
      <p class="ad-hoc-hint">
        将为 {{ includeDeviceIds.length }} 台设备提交纳入申请（仅列出明细中尚无该设备、且无待确认申请的已审核计划；可多选计划批量申请）。
      </p>
      <el-form :inline="true" class="include-filters" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="includeFilter.keyword"
            clearable
            placeholder="单号/名称/模板/科室/责任人"
            style="width: 200px"
            @keyup.enter="loadEligiblePlans"
          />
        </el-form-item>
        <el-form-item label="责任科室">
          <el-select
            v-model="includeFilter.dept_id"
            clearable
            filterable
            remote
            reserve-keyword
            placeholder="搜索科室"
            :remote-method="searchIncludeDepts"
            :loading="includeDeptLoading"
            style="width: 160px"
          >
            <el-option
              v-for="d in includeDepts"
              :key="String(d.id)"
              :label="String(d.dept_name || '')"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模板">
          <el-select
            v-model="includeFilter.template_id"
            clearable
            filterable
            placeholder="选择模板"
            style="width: 180px"
          >
            <el-option
              v-for="t in includeTemplates"
              :key="String(t.id)"
              :label="String(t.template_name || '')"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划状态">
          <el-select v-model="includeFilter.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="进行中" value="active" />
            <el-option label="暂停" value="paused" />
            <el-option label="待执行" value="pending" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次到期">
          <el-date-picker
            v-model="includeDueRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="起"
            end-placeholder="止"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="includePlanLoading" @click="loadEligiblePlans">查询</el-button>
          <el-button @click="resetIncludeFilter">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table
        :data="eligiblePlans"
        border
        size="small"
        height="360"
        v-loading="includePlanLoading"
        row-key="id"
        @selection-change="onIncludePlanSelection"
      >
        <el-table-column type="selection" width="45" fixed />
        <el-table-column prop="plan_no" label="计划单号" width="140" show-overflow-tooltip />
        <el-table-column prop="plan_name" label="计划名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="template_name" label="模板" min-width="120" show-overflow-tooltip />
        <el-table-column prop="type_label" :label="typeLabel" width="100" show-overflow-tooltip />
        <el-table-column prop="dept_name" label="责任科室" width="110" show-overflow-tooltip />
        <el-table-column prop="assigned_user_name" label="责任人" width="90" show-overflow-tooltip />
        <el-table-column label="周期" width="100">
          <template #default="{ row }">
            {{ formatPlanCycle(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="next_due_date" label="下次到期" width="110" />
        <el-table-column prop="item_count" label="明细数" width="70" align="center" />
        <el-table-column
          v-if="includeDeviceIds.length > 1"
          prop="eligible_device_count"
          label="可纳台数"
          width="80"
          align="center"
        />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column prop="created_by_name" label="制单人" width="90" show-overflow-tooltip />
      </el-table>
      <p class="include-selected-hint">已选 {{ includePlanIds.length }} 个计划</p>
      <el-form label-width="60px" style="margin-top: 8px">
        <el-form-item label="备注">
          <el-input v-model="includeRemark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="includeVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!includePlanIds.length" @click="submitInclude">提交申请</el-button>
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
import { calcCycleDays } from '@/utils/cycleDays'

const CHANNEL_LABELS: Record<string, string> = { web: 'Web', app: 'App', mp: '小程序' }
function channelLabel(v: unknown) {
  const s = v != null ? String(v).trim() : ''
  if (!s) return '—'
  return CHANNEL_LABELS[s] || s
}

const props = defineProps<{
  module: 'maintain' | 'inspect' | 'pm'
}>()

const titles = { maintain: '保养设备管理', inspect: '巡检设备管理', pm: '预防性维护设备管理' }
const tables = { maintain: 'ops_maintain_device', inspect: 'ops_inspect_device', pm: 'ops_pm_device' }
const typeLabel = computed(() =>
  props.module === 'maintain' ? '保养级别' : props.module === 'inspect' ? '巡检类型' : 'PM类型'
)
const typeMeta = computed(() => {
  if (props.module === 'maintain') {
    return {
      listUrl: '/maintain/maintenance_level/list',
      idField: 'maintenance_level_id',
      textField: 'maintenance_level',
      labelKey: 'level_name',
      codeKey: 'level_code',
      templateIdKey: 'maintenance_level_id',
      templateTextKeys: ['maintenance_level']
    }
  }
  if (props.module === 'inspect') {
    return {
      listUrl: '/inspect/inspection_type/list',
      idField: 'inspection_type_id',
      textField: 'inspection_type',
      labelKey: 'type_name',
      codeKey: 'type_code',
      templateIdKey: 'inspection_type_id',
      templateTextKeys: ['inspection_type', 'inspection_type_name', 'type_name']
    }
  }
  return {
    listUrl: '/maintain/pm_type/list',
    idField: 'pm_type_id',
    textField: 'pm_type',
    labelKey: 'type_name',
    codeKey: 'type_code',
    templateIdKey: 'pm_type_id',
    templateTextKeys: ['pm_type', 'type_name']
  }
})

function typeOptionLabel(o: Record<string, unknown>) {
  const meta = typeMeta.value
  const name = String(o[meta.labelKey] ?? '')
  const code = String(o[meta.codeKey] ?? '')
  if (name && code) return `${name}（${code}）`
  return name || code || String(o.id ?? '')
}

function matchTypeId(t: Record<string, unknown>) {
  const meta = typeMeta.value
  const fromId = String(t[meta.templateIdKey] ?? '')
  if (fromId && typeOptions.value.some((o) => String(o.id) === fromId)) return fromId
  for (const key of meta.templateTextKeys) {
    const v = String(t[key] ?? '')
    if (!v) continue
    const hit = typeOptions.value.find(
      (o) => String(o[meta.codeKey] ?? '') === v || String(o[meta.labelKey] ?? '') === v
    )
    if (hit) return String(hit.id)
  }
  return ''
}

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
        { key: 'executor_name', label: '执行人' },
        { key: 'create_channel', label: '制单途径' },
        { key: 'audit_channel', label: '审核途径' },
        { key: 'auditor_name', label: '审核人' },
        { key: 'audited_at', label: '审核时间' }
      ]
)

const adHocVisible = ref(false)
const adHocDeviceIds = ref<string[]>([])
const templates = ref<Record<string, unknown>[]>([])
const typeOptions = ref<Record<string, unknown>[]>([])
const adHoc = ref<Record<string, unknown>>({
  template_id: null,
  typeId: '',
  cycle_type: 'month',
  cycle_value: 1,
  cycle_days: 30,
  planned_date: null,
  execute_start_time: null,
  execute_end_time: null
})

function syncAdHocCycleDays() {
  adHoc.value.cycle_days = calcCycleDays(adHoc.value.cycle_type, adHoc.value.cycle_value)
}

function onAdHocDateChange(date: string | null) {
  if (!date) return
  adHoc.value.execute_start_time = `${date} 00:00:00`
  adHoc.value.execute_end_time = `${date} 23:59:59`
}

function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

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
  const [{ data: tplData }, { data: typeData }] = await Promise.all([
    http.get(`/${props.module}/${table}/list`),
    http.get(typeMeta.value.listUrl)
  ])
  templates.value = tplData.data ?? []
  typeOptions.value = typeData.data ?? []
  const date = todayStr()
  adHoc.value = {
    template_id: null,
    typeId: typeOptions.value[0] ? String(typeOptions.value[0].id) : '',
    cycle_type: 'month',
    cycle_value: 1,
    cycle_days: 30,
    planned_date: date,
    execute_start_time: `${date} 00:00:00`,
    execute_end_time: `${date} 23:59:59`
  }
  adHocVisible.value = true
}

function onTemplateChange() {
  const t = templates.value.find((x) => String(x.id) === String(adHoc.value.template_id))
  if (!t) return
  const matched = matchTypeId(t)
  if (matched) adHoc.value.typeId = matched
  if (t.cycle_type) adHoc.value.cycle_type = t.cycle_type
  if (t.cycle_value != null) adHoc.value.cycle_value = Number(t.cycle_value) || 1
  syncAdHocCycleDays()
}

async function submitAdHoc() {
  if (!adHocDeviceIds.value.length) return
  if (!adHoc.value.template_id) {
    ElMessage.warning('请选择模板')
    return
  }
  if (!adHoc.value.typeId) {
    ElMessage.warning(`请选择${typeLabel.value}`)
    return
  }
  if (!adHoc.value.cycle_type || !adHoc.value.cycle_value) {
    ElMessage.warning('请填写周期类型与周期值')
    return
  }
  if (!adHoc.value.planned_date) {
    ElMessage.warning('请填写执行日期')
    return
  }
  if (!adHoc.value.execute_start_time || !adHoc.value.execute_end_time) {
    ElMessage.warning('请填写开始/结束时间')
    return
  }
  const meta = typeMeta.value
  const typeRow = typeOptions.value.find((o) => String(o.id) === String(adHoc.value.typeId))
  const typeText = typeRow
    ? String(typeRow[meta.codeKey] || typeRow[meta.labelKey] || '')
    : ''
  const body: Record<string, unknown> = {
    template_id: adHoc.value.template_id,
    planned_date: adHoc.value.planned_date,
    cycle_type: adHoc.value.cycle_type,
    cycle_value: adHoc.value.cycle_value,
    execute_start_time: adHoc.value.execute_start_time,
    execute_end_time: adHoc.value.execute_end_time,
    client: 'web',
    [meta.idField]: adHoc.value.typeId,
    [meta.textField]: typeText,
    items: adHocDeviceIds.value.map((device_id) => ({ device_id }))
  }
  const { data } = await http.post(`/${props.module}/execution/ad-hoc`, body)
  const execNo = data.data?.execution_no
  ElMessage.success(execNo ? `已创建执行单 ${execNo}` : '已创建执行单')
  adHocVisible.value = false
  crudRef.value?.load()
}

const includeVisible = ref(false)
const includeDeviceIds = ref<string[]>([])
const includePlanIds = ref<string[]>([])
const includeRemark = ref('')
const eligiblePlans = ref<Record<string, unknown>[]>([])
const includePlanLoading = ref(false)
const includeDepts = ref<Record<string, unknown>[]>([])
const includeDeptLoading = ref(false)
const includeTemplates = ref<Record<string, unknown>[]>([])
const includeDueRange = ref<[string, string] | null>(null)
const includeFilter = ref({
  keyword: '',
  dept_id: '' as string | '',
  template_id: '' as string | '',
  status: '' as string | ''
})

const templateListUrl = computed(() => {
  if (props.module === 'inspect') return '/inspect/inspection_template/list'
  if (props.module === 'pm') return '/maintain/pm_template/list'
  return '/maintain/maintenance_template/list'
})

function formatPlanCycle(row: Record<string, unknown>) {
  const days = row.cycle_days
  if (days != null && String(days) !== '') return `${days} 天`
  const t = row.cycle_type
  const v = row.cycle_value
  if (t || v) return `${t ?? ''}${v != null ? `×${v}` : ''}`
  return '—'
}

async function searchIncludeDepts(keyword: string) {
  includeDeptLoading.value = true
  try {
    const { data } = await http.get('/system/departments', {
      params: { keyword: keyword || undefined, page: 1, size: 50 }
    })
    includeDepts.value = data.data?.records ?? data.data?.list ?? data.data ?? []
  } catch {
    includeDepts.value = []
  } finally {
    includeDeptLoading.value = false
  }
}

async function loadIncludeTemplates() {
  try {
    const { data } = await http.get(templateListUrl.value)
    includeTemplates.value = data.data ?? []
  } catch {
    includeTemplates.value = []
  }
}

async function loadEligiblePlans() {
  if (!includeDeviceIds.value.length) {
    eligiblePlans.value = []
    return
  }
  includePlanLoading.value = true
  try {
    const [from, to] = includeDueRange.value ?? [undefined, undefined]
    const { data } = await http.get(`/${props.module}/plan/include-request/approved-plans`, {
      params: {
        device_ids: includeDeviceIds.value.join(','),
        keyword: includeFilter.value.keyword || undefined,
        dept_id: includeFilter.value.dept_id || undefined,
        template_id: includeFilter.value.template_id || undefined,
        status: includeFilter.value.status || undefined,
        next_due_from: from || undefined,
        next_due_to: to || undefined
      }
    })
    eligiblePlans.value = data.data ?? []
    includePlanIds.value = []
  } finally {
    includePlanLoading.value = false
  }
}

function resetIncludeFilter() {
  includeFilter.value = { keyword: '', dept_id: '', template_id: '', status: '' }
  includeDueRange.value = null
  void loadEligiblePlans()
}

function onIncludePlanSelection(rows: Record<string, unknown>[]) {
  includePlanIds.value = rows.map((r) => String(r.id)).filter(Boolean)
}

async function openInclude(deviceIds: string[]) {
  includeDeviceIds.value = deviceIds
  includePlanIds.value = []
  includeRemark.value = ''
  includeFilter.value = { keyword: '', dept_id: '', template_id: '', status: '' }
  includeDueRange.value = null
  await Promise.all([loadIncludeTemplates(), searchIncludeDepts(''), loadEligiblePlans()])
  includeVisible.value = true
}

async function onBatchInclude() {
  const count = readSelectedCount(crudRef.value)
  const scope = await promptListActionScope(count, '申请纳入')
  if (!scope || !assertScopeSelection(scope, count)) return
  const ids = await resolveDeviceIds(scope)
  if (!ids.length) {
    ElMessage.warning('没有可选设备')
    return
  }
  await openInclude(ids)
}

async function submitInclude() {
  if (!includePlanIds.value.length) {
    ElMessage.warning('请勾选目标计划')
    return
  }
  if (!includeDeviceIds.value.length) return
  try {
    const { data } = await http.post(`/${props.module}/plan/include-request/batch`, {
      client: 'web',
      plan_ids: includePlanIds.value,
      device_ids: includeDeviceIds.value,
      remark: includeRemark.value
    })
    const ok = Number(data.data?.ok ?? 0)
    const skip = Number(data.data?.skip ?? 0)
    ElMessage.success(`已提交 ${ok} 条申请${skip ? `，跳过 ${skip}` : ''}`)
    includeVisible.value = false
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '提交失败')
  }
}
</script>

<style scoped>
.ad-hoc-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.include-filters {
  margin-bottom: 8px;
}
.include-selected-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.doc-desc {
  margin-bottom: 12px;
}
.doc-items {
  margin-top: 8px;
}
</style>
