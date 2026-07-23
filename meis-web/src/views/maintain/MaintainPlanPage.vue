<template>
  <WorkflowCrudPage
    ref="pageRef"
    :config="config"
    save-url="/maintain/plan"
    hide-operation-column
    :can-edit="isDraft"
    :can-delete="isDraft"
  >
    <template #list-toolbar-extra>
      <el-button @click="loadDue">到期提醒</el-button>
    </template>
    <template #extra-columns>
      <el-table-column label="审核" width="110" fixed="right" align="center" header-align="center">
        <template #default="{ row }">
          <template v-if="row.approval_status === 'draft'">
            <el-button link type="primary" @click="approveRow(row)">通过</el-button>
            <el-button link @click="rejectRow(row)">驳回</el-button>
          </template>
          <span v-else class="op-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="设备明细" width="90" fixed="right" align="center" header-align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openItems(row)">明细</el-button>
        </template>
      </el-table-column>
      <el-table-column label="生成执行" width="90" fixed="right" align="center" header-align="center">
        <template #default="{ row }">
          <el-button v-if="isApproved(row)" link type="success" @click="genExecRow(row)">生成</el-button>
          <span v-else class="op-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="编辑" width="70" fixed="right" align="center" header-align="center">
        <template #default="{ row }">
          <el-button v-if="isDraft(row)" link type="primary" @click="pageRef?.openDetail(row)">编辑</el-button>
          <span v-else class="op-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="删除" width="70" fixed="right" align="center" header-align="center">
        <template #default="{ row }">
          <el-button v-if="isDraft(row)" link type="danger" @click="pageRef?.remove(row)">删除</el-button>
          <span v-else class="op-muted">—</span>
        </template>
      </el-table-column>
    </template>
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id && form.approval_status === 'draft'" type="primary" @click="approve(form, reload)">审核通过</el-button>
      <el-button v-if="form?.id && form.approval_status === 'draft'" @click="reject(form, reload)">驳回</el-button>
      <el-button v-if="form?.id && form.approval_status === 'approved'" type="success" @click="genExec(form, reload)">生成执行单（到期明细）</el-button>
      <el-button v-if="form?.id" @click="activate(form, reload)">激活计划</el-button>
    </template>
    <template #drawer-extra="{ form }">
      <FormSection title="设备明细（权威到期日）" class="items-section">
        <div class="items-toolbar">
          <el-button type="primary" size="small" @click="addDevice(form)">添加设备</el-button>
        </div>
        <el-table :data="(form.items as Record<string, unknown>[]) || []" border size="small">
          <el-table-column prop="device_code" label="设备编码" width="120" />
          <el-table-column prop="device_name" label="设备名称" min-width="140" />
          <el-table-column label="上次完成" width="140">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.last_done_date"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                style="width: 100%"
                @change="() => onLastDoneChange(form, row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="下次到期" width="140">
            <template #default="{ row }">
              <el-date-picker v-model="row.next_due_date" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeItem(form, $index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </FormSection>
      <el-alert v-if="dueList.length" :title="`近7天到期 ${dueList.length} 项`" type="warning" show-icon class="due-alert" />
    </template>
  </WorkflowCrudPage>

  <el-dialog v-model="pickerVisible" title="选择设备" width="640px" destroy-on-close append-to-body>
    <el-input v-model="pickerKw" placeholder="编码/名称" clearable style="margin-bottom: 8px" @keyup.enter="searchDevices" />
    <el-table :data="pickerRows" border size="small" max-height="360" @row-click="pickDevice">
      <el-table-column prop="device_code" label="编码" width="120" />
      <el-table-column prop="device_name" label="名称" min-width="160" />
      <el-table-column prop="dept_name" label="科室" width="120" />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import FormSection from '@/components/form/FormSection.vue'
import { calcItemNextDueDate } from '@/utils/cycleDays'
import type { PageConfig } from '@/config/pageRegistry'

const auth = useAuthStore()
const pageRef = ref<InstanceType<typeof WorkflowCrudPage> | null>(null)
const config: PageConfig = {
  title: '保养计划',
  apiBase: '/maintain',
  table: 'maintenance_plan',
  saveUrl: '/maintain/plan',
  loadFormDetail: true,
  showRowIndex: true,
  showRowSelection: true
}
const dueList = ref<Record<string, unknown>[]>([])
const pickerVisible = ref(false)
const pickerKw = ref('')
const pickerRows = ref<Record<string, unknown>[]>([])
const activeForm = ref<Record<string, unknown> | null>(null)

function isDraft(row: Record<string, unknown>) {
  return row.approval_status !== 'approved'
}

function isApproved(row: Record<string, unknown>) {
  return row.approval_status === 'approved'
}

function openItems(row: Record<string, unknown>) {
  pageRef.value?.openItemsOnly(row)
}

async function approveRow(row: Record<string, unknown>) {
  await approve(row, () => pageRef.value?.load())
}

async function rejectRow(row: Record<string, unknown>) {
  await reject(row, () => pageRef.value?.load())
}

async function genExecRow(row: Record<string, unknown>) {
  await genExec(row, () => pageRef.value?.load())
}

async function approve(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/maintain/plan/${form.id}/approve`, { action: 'approve', approved_by: auth.user?.id })
  ElMessage.success('审核通过')
  reload?.()
}

async function reject(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/maintain/plan/${form.id}/approve`, { action: 'reject', approved_by: auth.user?.id })
  ElMessage.success('已驳回')
  reload?.()
}

async function genExec(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/maintain/plan/${form.id}/generate-execution`, { created_by: auth.user?.id })
  ElMessage.success('已生成保养执行单')
  reload?.()
}

async function activate(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/maintain/plan/${form.id}/activate`)
  reload?.()
}

async function loadDue() {
  const { data } = await http.get('/maintain/plan/due')
  dueList.value = data.data ?? []
}

function ensureItems(form: Record<string, unknown>) {
  if (!Array.isArray(form.items)) form.items = []
  return form.items as Record<string, unknown>[]
}

function onLastDoneChange(form: Record<string, unknown>, row: Record<string, unknown>) {
  row.next_due_date = calcItemNextDueDate(form, row.last_done_date)
}

function addDevice(form: Record<string, unknown>) {
  activeForm.value = form
  ensureItems(form)
  pickerVisible.value = true
  searchDevices()
}

async function searchDevices() {
  const { data } = await http.get('/asset/device/page', {
    params: { page: 1, size: 50, keyword: pickerKw.value || undefined }
  })
  pickerRows.value = data.data?.records ?? data.data?.list ?? data.data?.rows ?? []
}

function pickDevice(row: Record<string, unknown>) {
  if (!activeForm.value) return
  const items = ensureItems(activeForm.value)
  if (items.some((i) => String(i.device_id) === String(row.id))) {
    ElMessage.warning('设备已在明细中')
    return
  }
  items.push({
    device_id: row.id,
    device_code: row.device_code,
    device_name: row.device_name,
    dept_id: row.dept_id,
    last_done_date: null,
    next_due_date: calcItemNextDueDate(activeForm.value, null),
    item_status: 'active'
  })
  pickerVisible.value = false
}

function removeItem(form: Record<string, unknown>, index: number) {
  ensureItems(form).splice(index, 1)
}
</script>

<style scoped>
.items-section { margin-top: 16px; }
.items-toolbar { margin-bottom: 8px; }
.due-alert { margin-top: 12px; }
.op-muted { color: var(--el-text-color-placeholder); }
</style>
