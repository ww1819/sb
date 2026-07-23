<template>
  <div class="workflow-crud">
    <CrudPage
      ref="crudRef"
      :config="config"
      detail-mode
      hide-add
      hide-operation-column
      @detail="openEdit"
      @view="openView"
    >
      <template #extra-columns>
        <el-table-column label="查看" width="70" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column label="编辑" width="70" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button v-if="canEditRow(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
            <span v-else class="op-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="执行" width="70" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button v-if="canExecuteRow(row)" link type="primary" @click="openExecute(row)">执行</el-button>
            <span v-else class="op-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="审核" width="70" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button v-if="canAuditRow(row)" link type="primary" @click="auditRow(row)">通过</el-button>
            <span v-else class="op-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="删除" width="70" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button v-if="canDeleteRow(row)" link type="danger" @click="removeRow(row)">删除</el-button>
            <span v-else class="op-muted">—</span>
          </template>
        </el-table-column>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="exec">
        <el-form
          v-if="openMode !== 'execute'"
          class="ops-doc-form"
          :disabled="formReadonly"
          label-position="left"
          label-width="100px"
          size="small"
        >
          <GroupedFormFields
            :table="config.table"
            :model="exec"
            :group-columns="{ basic: 4, workflow: 4, approval: 4 }"
            flow
          />
        </el-form>
        <div v-if="exec" class="header-channels">
          制单途径 {{ channelLabel(exec.create_channel) }}
          · 审核途径 {{ channelLabel(exec.audit_channel) }}
          · 审核人 {{ blankDash(exec.auditor_name) }}
          · 审核时间 {{ blankDash(exec.audited_at) }}
        </div>
        <FormSection title="设备明细" class="items-section">
          <el-table :data="execItems" border size="small">
            <el-table-column prop="device_code" label="设备编码" width="110" />
            <el-table-column prop="device_name" label="设备名称" min-width="120" />
            <el-table-column prop="dept_name" label="科室" width="100" />
            <el-table-column prop="executor_name" label="执行人" width="90" />
            <el-table-column prop="end_time" label="执行时间" width="160" />
            <el-table-column label="确认状态" width="90">
              <template #default="{ row }">
                {{ String(row.status) === 'confirmed' ? '已确认' : '未确认' }}
              </template>
            </el-table-column>
            <el-table-column prop="confirmed_by_name" label="确认人" width="90" />
            <el-table-column prop="confirmed_at" label="确认时间" width="160" />
            <el-table-column label="执行途径" width="90">
              <template #default="{ row }">{{ channelLabel(row.execution_channel) }}</template>
            </el-table-column>
            <el-table-column label="确认途径" width="90">
              <template #default="{ row }">{{ channelLabel(row.confirm_channel) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canExecuteItem(row)"
                  link
                  type="primary"
                  @click="openItem(row, false)"
                >{{ executeItemLabel(row) }}</el-button>
                <el-button
                  v-if="canConfirmItem(row)"
                  link
                  type="success"
                  @click="confirmItem(row)"
                >确认</el-button>
                <el-button link @click="openItem(row, true)">查看</el-button>
                <el-button
                  v-if="canDeleteItem(row)"
                  link
                  type="danger"
                  @click="deleteItem(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </FormSection>
      </template>
      <template #header-actions>
        <el-button v-if="exec?.id" @click="changeLogVisible = true">修改记录</el-button>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="openMode === 'edit' && editable" type="primary" @click="saveHeader">保存</el-button>
      </template>
    </AppModal>

    <DocChangeHistoryDrawer
      v-model="changeLogVisible"
      :api-url="exec?.id ? `/inspect/execution/${exec.id}/change-logs` : ''"
    />

    <AppModal v-model="itemVisible" :title="itemViewOnly ? '查看设备巡检' : '设备巡检执行'" size="lg">
      <template v-if="currentItem">
        <div class="item-header">
          {{ currentItem.device_code }} · {{ currentItem.device_name }}
          <span class="item-channels">
            执行途径 {{ channelLabel(currentItem.execution_channel) }}
            · 确认途径 {{ channelLabel(currentItem.confirm_channel) }}
          </span>
        </div>
        <el-table :data="itemResults" border size="small">
          <el-table-column prop="item_name" label="巡检项目" min-width="140" />
          <el-table-column prop="item_content" label="巡检内容" min-width="160" show-overflow-tooltip />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <el-select v-model="row.result_status" size="small" :disabled="itemResultReadonly">
                <el-option label="合格" value="pass" />
                <el-option label="不合格" value="fail" />
                <el-option label="不适用" value="na" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="实测值" width="140">
            <template #default="{ row }">
              <el-input v-model="row.result_value" size="small" :disabled="itemResultReadonly" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="itemVisible = false">关闭</el-button>
        <el-button v-if="!itemResultReadonly" type="primary" @click="completeItem">
          {{ String(currentItem?.status) === 'completed' ? '保存修改' : '完成' }}
        </el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import AppModal from '@/components/AppModal.vue'
import DocChangeHistoryDrawer from '@/components/DocChangeHistoryDrawer.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { useDict } from '@/composables/useDict'

const CLIENT = { client: 'web' }
const { loadDict, resolveDictLabel } = useDict()

function channelLabel(v: unknown) {
  return resolveDictLabel('execution_channel', v) || (v != null && String(v) !== '' ? String(v) : '—')
}

function blankDash(v: unknown) {
  if (v == null || String(v).trim() === '') return '—'
  return String(v)
}

onMounted(() => {
  void loadDict('execution_channel')
})

const config: PageConfig = {
  title: '巡检执行',
  apiBase: '/inspect',
  table: 'inspection_execution',
  listPageUrl: '/inspect/execution/page',
  listFilters: [
    { key: 'status', label: '状态', dictType: 'inspect_exec_status', multiple: true },
    {
      key: 'source_type',
      label: '来源',
      multiple: true,
      options: [
        { value: 'from_plan', label: '计划生成' },
        { value: 'ad_hoc', label: '直开' }
      ]
    },
    { key: 'execution_kind', label: '执行类型', dictType: 'ops_execution_kind', multiple: true },
    { key: 'create_channel', label: '制单途径', dictType: 'execution_channel', multiple: true },
    { key: 'planned_date', label: '计划/执行日期', type: 'daterange' }
  ]
}

const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const changeLogVisible = ref(false)
const openMode = ref<'view' | 'edit' | 'execute'>('edit')
const exec = ref<Record<string, unknown> | null>(null)
const execItems = ref<Record<string, unknown>[]>([])
const itemVisible = ref(false)
const itemViewOnly = ref(false)
const currentItem = ref<Record<string, unknown> | null>(null)
const itemResults = ref<Record<string, unknown>[]>([])

const editable = computed(() => {
  const s = String(exec.value?.status ?? '')
  return s === 'draft' || s === 'pending' || s === 'in_progress'
})

const formReadonly = computed(() => openMode.value === 'view')

const modalTitle = computed(() => {
  if (openMode.value === 'execute') return '巡检执行'
  if (openMode.value === 'view') return '巡检执行详情'
  return '巡检执行编辑'
})

const itemResultReadonly = computed(() => {
  if (openMode.value !== 'execute' || itemViewOnly.value) return true
  const st = String(currentItem.value?.status ?? '')
  return st === 'confirmed' || !editable.value
})

function canEditRow(row: Record<string, unknown>) {
  const s = String(row.status ?? '')
  return s === 'draft' || s === 'pending' || s === 'in_progress'
}

function canExecuteRow(row: Record<string, unknown>) {
  return canEditRow(row)
}

function truthyFlag(v: unknown) {
  return v === true || v === 1 || v === '1' || v === 't' || v === 'true'
}

/** OPS.16.18：明细已有执行痕迹 */
function itemHasExecutionRecord(row: Record<string, unknown>) {
  const st = String(row.status ?? '')
  if (st === 'completed' || st === 'confirmed') return true
  if (row.end_time != null && String(row.end_time) !== '') return true
  if (row.executor_id != null && String(row.executor_id) !== '') return true
  const result = row.overall_result
  return result != null && String(result).trim() !== ''
}

function canDeleteRow(row: Record<string, unknown>) {
  // OPS.16.18：草稿且无任何明细执行记录才可删
  return String(row.status ?? '') === 'draft' && !truthyFlag(row.has_execution_record)
}

function canAuditRow(row: Record<string, unknown>) {
  const s = String(row.status ?? '')
  return s !== 'audited' && s !== 'cancelled' && s !== ''
}

function canExecuteItem(row: Record<string, unknown>) {
  if (openMode.value !== 'execute' || !editable.value) return false
  // OPS.16.17：未确认均可执行/修改；已确认锁定
  return String(row.status ?? '') !== 'confirmed'
}

function executeItemLabel(row: Record<string, unknown>) {
  return String(row.status ?? '') === 'completed' ? '修改' : '执行'
}

function canConfirmItem(row: Record<string, unknown>) {
  if (openMode.value !== 'execute' || !editable.value) return false
  return String(row.status ?? '') !== 'confirmed'
}

function canDeleteItem(row: Record<string, unknown>) {
  if (openMode.value !== 'edit' || !editable.value) return false
  return !itemHasExecutionRecord(row)
}

async function loadExec(row: Record<string, unknown>) {
  const { data } = await http.get(`/inspect/execution/${row.id}`)
  exec.value = data.data ?? { ...row }
  execItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  visible.value = true
}

async function openEdit(row: Record<string, unknown>) {
  openMode.value = 'edit'
  await loadExec(row)
}

async function openView(row: Record<string, unknown>) {
  openMode.value = 'view'
  await loadExec(row)
}

async function openExecute(row: Record<string, unknown>) {
  openMode.value = 'execute'
  await loadExec(row)
}

async function openDetail(row: Record<string, unknown>) {
  await openEdit(row)
}

function openItem(row: Record<string, unknown>, viewOnly: boolean) {
  currentItem.value = row
  itemResults.value = (row.results as Record<string, unknown>[]) ?? []
  itemViewOnly.value = viewOnly || !canExecuteItem(row)
  itemVisible.value = true
}

async function saveHeader() {
  if (!exec.value?.id) return
  await http.put(`/inspect/execution/${exec.value.id}`, {
    ...CLIENT,
    remark: exec.value.remark,
    execute_start_time: exec.value.execute_start_time,
    execute_end_time: exec.value.execute_end_time
  })
  ElMessage.success('已保存')
  await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function confirmItem(row: Record<string, unknown>) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(
      String(row.status) === 'completed'
        ? '确认该设备执行结果？'
        : '结果未填完也可确认，将自动记为已完成再确认。是否继续？',
      '确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await http.post(`/inspect/execution/item/${row.id}/confirm`, CLIENT)
  ElMessage.success('已确认')
  if (exec.value?.id) await openExecute({ id: exec.value.id })
  crudRef.value?.load()
}

async function deleteItem(row: Record<string, unknown>) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确认删除该设备明细？', '删除', { type: 'warning' })
  } catch {
    return
  }
  await http.delete(`/inspect/execution/item/${row.id}`, { params: CLIENT })
  ElMessage.success('已删除')
  if (exec.value?.id) await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function removeRow(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确认删除该执行单？', '删除', { type: 'warning' })
    await http.delete(`/inspect/execution/${row.id}`, { params: CLIENT })
    ElMessage.success('已删除')
    crudRef.value?.load()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(msg || '删除失败')
    }
  }
}

async function auditRow(row: Record<string, unknown>) {
  await http.post(`/inspect/execution/${row.id}/audit`, { ...CLIENT, action: 'approve' })
  ElMessage.success('审核通过')
  crudRef.value?.load()
}

async function withdrawExec() {
  if (!exec.value?.id) return
  await http.post(`/inspect/execution/${exec.value.id}/withdraw`, CLIENT)
  ElMessage.success('已撤回')
  await openView({ id: exec.value.id })
  crudRef.value?.load()
}

async function completeItem() {
  if (!currentItem.value?.id) return
  const hasFail = itemResults.value.some((r) => r.result_status === 'fail')
  await http.post(`/inspect/execution/item/${currentItem.value.id}/complete`, {
    ...CLIENT,
    results: itemResults.value,
    overall_result: hasFail ? 'fail' : 'pass'
  })
  ElMessage.success('设备保养已完成')
  itemVisible.value = false
  if (exec.value?.id) await openExecute({ id: exec.value.id })
  crudRef.value?.load()
}
</script>

<style scoped>
.items-section { margin-top: 12px; }
.header-channels {
  margin: 8px 0 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.item-header { margin-bottom: 12px; font-weight: 600; }
.item-channels { margin-left: 12px; font-weight: 400; color: var(--el-text-color-secondary); font-size: 13px; }
.op-muted { color: var(--el-text-color-placeholder); }
.ops-doc-form :deep(.form-section) {
  margin-bottom: 6px;
}
.ops-doc-form :deep(.form-section__title) {
  font-size: 13px;
  padding-bottom: 2px;
  margin-bottom: 4px;
}
.ops-doc-form :deep(.form-grid--flow .el-form-item) {
  margin-bottom: 2px;
}
</style>
