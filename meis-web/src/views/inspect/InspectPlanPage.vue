<template>
  <WorkflowCrudPage ref="pageRef" :config="config" save-url="/inspect/plan">
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id && form.approval_status === 'draft'" type="primary" @click="approve(form, reload)">审核通过</el-button>
      <el-button v-if="form?.id && form.approval_status === 'approved'" type="success" @click="genExec(form, reload)">生成执行单（到期明细）</el-button>
      <el-button @click="loadDue">到期提醒</el-button>
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
              <el-date-picker v-model="row.last_done_date" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
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

  <el-dialog v-model="pickerVisible" title="选择设备" width="640px" destroy-on-close>
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
import type { PageConfig } from '@/config/pageRegistry'

const auth = useAuthStore()
const config: PageConfig = {
  title: '巡检计划',
  apiBase: '/inspect',
  table: 'inspection_plan',
  saveUrl: '/inspect/plan',
  loadFormDetail: true,
  showRowIndex: true,
  showRowSelection: true
}
const dueList = ref<Record<string, unknown>[]>([])
const pickerVisible = ref(false)
const pickerKw = ref('')
const pickerRows = ref<Record<string, unknown>[]>([])
const activeForm = ref<Record<string, unknown> | null>(null)

async function approve(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/inspect/plan/${form.id}/approve`, { action: 'approve', approved_by: auth.user?.id })
  ElMessage.success('审核通过')
  reload?.()
}

async function genExec(form: Record<string, unknown>, reload?: () => void) {
  await http.post(`/inspect/plan/${form.id}/generate-execution`, { created_by: auth.user?.id })
  ElMessage.success('已生成巡检执行单')
  reload?.()
}

async function loadDue() {
  const { data } = await http.get('/inspect/plan/due')
  dueList.value = data.data ?? []
}

function ensureItems(form: Record<string, unknown>) {
  if (!Array.isArray(form.items)) form.items = []
  return form.items as Record<string, unknown>[]
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
    next_due_date: null,
    last_done_date: null,
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
</style>
