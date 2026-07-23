<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail" />

    <AppModal v-model="visible" title="预防性维护执行详情" size="xl">
      <template v-if="exec">
        <GroupedFormFields :table="config.table" :model="exec" />
        <FormSection title="设备明细" class="items-section">
          <el-table :data="execItems" border size="small">
            <el-table-column prop="device_code" label="设备编码" width="120" />
            <el-table-column prop="device_name" label="设备名称" min-width="140" />
            <el-table-column prop="dept_name" label="科室" width="120" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="editable && row.status !== 'completed'"
                  link
                  type="primary"
                  @click="openItem(row)"
                >执行</el-button>
                <el-button v-else link @click="openItem(row)">查看</el-button>
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
        <el-button v-if="exec && ['draft','pending'].includes(String(exec.status))" type="primary" @click="startExec">开始执行</el-button>
        <el-button v-if="exec && ['draft','pending','in_progress'].includes(String(exec.status))" type="success" @click="submitExec">提交</el-button>
        <el-button v-if="exec?.status === 'submitted'" @click="withdrawExec">撤回</el-button>
        <el-button v-if="exec?.status === 'submitted'" type="warning" @click="auditExec">审核通过</el-button>
      </template>
    </AppModal>

    <DocChangeHistoryDrawer
      v-model="changeLogVisible"
      :api-url="exec?.id ? `/pm/execution/${exec.id}/change-logs` : ''"
    />

    <AppModal v-model="itemVisible" title="设备预防性维护执行" size="lg">
      <template v-if="currentItem">
        <div class="item-header">{{ currentItem.device_code }} · {{ currentItem.device_name }}</div>
        <el-table :data="itemResults" border size="small">
          <el-table-column prop="item_name" label="维护项目" min-width="140" />
          <el-table-column prop="item_content" label="维护内容" min-width="160" show-overflow-tooltip />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <el-select v-model="row.result_status" size="small" :disabled="!editable || currentItem.status === 'completed'">
                <el-option label="合格" value="pass" />
                <el-option label="不合格" value="fail" />
                <el-option label="不适用" value="na" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="实测值" width="140">
            <template #default="{ row }">
              <el-input v-model="row.result_value" size="small" :disabled="!editable || currentItem.status === 'completed'" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="itemVisible = false">关闭</el-button>
        <el-button v-if="editable && currentItem?.status !== 'completed'" type="primary" @click="completeItem">完成</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import AppModal from '@/components/AppModal.vue'
import DocChangeHistoryDrawer from '@/components/DocChangeHistoryDrawer.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '预防性维护执行',
  apiBase: '/pm',
  table: 'pm_execution',
  listPageUrl: '/pm/execution/page'
}

const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const changeLogVisible = ref(false)
const exec = ref<Record<string, unknown> | null>(null)
const execItems = ref<Record<string, unknown>[]>([])
const itemVisible = ref(false)
const currentItem = ref<Record<string, unknown> | null>(null)
const itemResults = ref<Record<string, unknown>[]>([])
const adHocVisible = ref(false)

const editable = computed(() => {
  const s = String(exec.value?.status ?? '')
  return s === 'draft' || s === 'pending' || s === 'in_progress'
})

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/pm/execution/${row.id}`)
  exec.value = data.data ?? { ...row }
  execItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  visible.value = true
}

function openItem(row: Record<string, unknown>) {
  currentItem.value = row
  itemResults.value = (row.results as Record<string, unknown>[]) ?? []
  itemVisible.value = true
}

async function startExec() {
  if (!exec.value?.id) return
  await http.post(`/pm/execution/${exec.value.id}/start`, {})
  ElMessage.success('已开始执行')
  await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function submitExec() {
  if (!exec.value?.id) return
  await http.post(`/pm/execution/${exec.value.id}/submit`, {})
  ElMessage.success('已提交')
  await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function withdrawExec() {
  if (!exec.value?.id) return
  await http.post(`/pm/execution/${exec.value.id}/withdraw`, {})
  ElMessage.success('已撤回')
  await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function auditExec() {
  if (!exec.value?.id) return
  await http.post(`/pm/execution/${exec.value.id}/audit`, { action: 'approve' })
  ElMessage.success('审核通过')
  await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function completeItem() {
  if (!currentItem.value?.id) return
  const hasFail = itemResults.value.some((r) => r.result_status === 'fail')
  await http.post(`/pm/execution/item/${currentItem.value.id}/complete`, {
    results: itemResults.value,
    overall_result: hasFail ? 'fail' : 'pass'
  })
  ElMessage.success('已完成')
  itemVisible.value = false
  if (exec.value?.id) await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}
</script>

<style scoped>
.items-section { margin-top: 16px; }
.item-header { margin-bottom: 12px; font-weight: 600; }
</style>
