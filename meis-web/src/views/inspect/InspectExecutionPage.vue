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
        <el-table-column label="审核" width="110" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <template v-if="canAuditRow(row)">
              <el-button link type="primary" @click="auditRow(row, 'approve')">通过</el-button>
              <el-button link @click="auditRow(row, 'reject')">驳回</el-button>
            </template>
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
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canExecuteItem(row)"
                  link
                  type="primary"
                  @click="openItem(row, false)"
                >执行</el-button>
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
        <el-button
          v-if="openMode === 'execute' && exec && ['draft','pending'].includes(String(exec.status))"
          type="primary"
          @click="startExec"
        >开始执行</el-button>
        <el-button v-if="openMode === 'view' && exec?.status === 'submitted'" @click="withdrawExec">撤回</el-button>
        <template v-if="openMode === 'view' && canAuditRow(exec || {})">
          <el-button type="warning" @click="auditExec('approve')">审核通过</el-button>
          <el-button @click="auditExec('reject')">驳回</el-button>
        </template>
      </template>
    </AppModal>

    <DocChangeHistoryDrawer
      v-model="changeLogVisible"
      :api-url="exec?.id ? `/inspect/execution/${exec.id}/change-logs` : ''"
    />

    <AppModal v-model="itemVisible" :title="itemViewOnly ? '查看设备巡检' : '设备巡检执行'" size="lg">
      <template v-if="currentItem">
        <div class="item-header">{{ currentItem.device_code }} · {{ currentItem.device_name }}</div>
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
        <el-button v-if="!itemResultReadonly" type="primary" @click="completeItem">完成</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import AppModal from '@/components/AppModal.vue'
import DocChangeHistoryDrawer from '@/components/DocChangeHistoryDrawer.vue'
import type { PageConfig } from '@/config/pageRegistry'

const CLIENT = { client: 'web' }

const config: PageConfig = {
  title: '巡检执行',
  apiBase: '/inspect',
  table: 'inspection_execution',
  listPageUrl: '/inspect/execution/page'
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
  return st === 'completed' || st === 'confirmed' || !editable.value
})

function canEditRow(row: Record<string, unknown>) {
  const s = String(row.status ?? '')
  return s === 'draft' || s === 'pending' || s === 'in_progress'
}

function canExecuteRow(row: Record<string, unknown>) {
  return canEditRow(row)
}

function canDeleteRow(row: Record<string, unknown>) {
  return String(row.status ?? '') === 'draft'
}

function canAuditRow(row: Record<string, unknown>) {
  const s = String(row.status ?? '')
  return s !== 'audited' && s !== 'cancelled' && s !== ''
}

function canExecuteItem(row: Record<string, unknown>) {
  if (openMode.value !== 'execute' || !editable.value) return false
  const s = String(row.status ?? '')
  return s !== 'completed' && s !== 'confirmed'
}

function canConfirmItem(row: Record<string, unknown>) {
  if (openMode.value !== 'execute' || !editable.value) return false
  return String(row.status ?? '') !== 'confirmed'
}

function canDeleteItem(row: Record<string, unknown>) {
  if (openMode.value !== 'edit' || !editable.value) return false
  return String(row.status ?? '') !== 'confirmed'
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
    await http.delete(`/inspect/inspection_execution/${row.id}`, { params: CLIENT })
    ElMessage.success('已删除')
    crudRef.value?.load()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(msg || '删除失败')
    }
  }
}

async function auditRow(row: Record<string, unknown>, action: 'approve' | 'reject') {
  await http.post(`/inspect/execution/${row.id}/audit`, { ...CLIENT, action })
  ElMessage.success(action === 'approve' ? '审核通过' : '已驳回')
  crudRef.value?.load()
}

async function startExec() {
  if (!exec.value?.id) return
  await http.post(`/inspect/execution/${exec.value.id}/start`, CLIENT)
  ElMessage.success('已开始执行')
  await openExecute({ id: exec.value.id })
  crudRef.value?.load()
}

async function withdrawExec() {
  if (!exec.value?.id) return
  await http.post(`/inspect/execution/${exec.value.id}/withdraw`, CLIENT)
  ElMessage.success('已撤回')
  await openView({ id: exec.value.id })
  crudRef.value?.load()
}

async function auditExec(action: 'approve' | 'reject' = 'approve') {
  if (!exec.value?.id) return
  await http.post(`/inspect/execution/${exec.value.id}/audit`, { ...CLIENT, action })
  ElMessage.success(action === 'approve' ? '审核通过' : '已驳回')
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
.item-header { margin-bottom: 12px; font-weight: 600; }
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
