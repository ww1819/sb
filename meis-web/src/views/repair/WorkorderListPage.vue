<template>
  <div class="workflow-crud">
    <CrudPage
      ref="crudRef"
      :config="config"
      detail-mode
      hide-add
      @detail="openDetail"
      @add="openCreate"
    >
      <template #toolbar-extra>
        <el-button type="primary" @click="openCreate">新增</el-button>
        <el-button v-if="wo?.id" @click="dispatch">派工</el-button>
        <el-button v-if="wo?.id" @click="act('accept')">接单</el-button>
        <el-button v-if="wo?.id" @click="act('complete')">完工</el-button>
        <el-button v-if="wo?.id" @click="act('verify')">验收</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="wo">
        <GroupedFormFields :table="config.table" :model="wo" :fields="formFields" />
        <FormSection v-if="wo.id" title="工单时间轴" class="timeline-section">
          <el-timeline>
            <el-timeline-item v-for="e in timeline" :key="e.label" :timestamp="e.time" placement="top">
              {{ e.label }}
            </el-timeline-item>
          </el-timeline>
        </FormSection>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="editable" type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const config: PageConfig = { title: '维修工单', apiBase: '/repair', table: 'repair_workorder' }
const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const wo = ref<Record<string, unknown> | null>(null)

const editable = computed(() => !wo.value?.id || wo.value.status === 'reported')
const modalTitle = computed(() => {
  if (!wo.value?.id) return '维修工单 新增'
  return editable.value ? '维修工单 编辑' : '维修工单 详情'
})
const formFields = computed(() => {
  const fields = getSchema('repair_workorder')
  if (editable.value) return fields
  return fields.map((f) => ({ ...f, readonly: true }))
})

const timeline = computed(() => {
  if (!wo.value?.id) return []
  const w = wo.value
  return [
    { label: '报修', time: fmt(w.report_time) },
    { label: '派工', time: fmt(w.assigned_at) },
    { label: '响应', time: fmt(w.response_time) },
    { label: '到场', time: fmt(w.arrival_time) },
    { label: '维修开始', time: fmt(w.repair_start_time) },
    { label: '维修结束', time: fmt(w.repair_end_time) },
    { label: '验收', time: fmt(w.verify_time) }
  ].filter((e) => e.time)
})

function fmt(v: unknown) {
  return v ? String(v).slice(0, 19) : ''
}

function nowText() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function openCreate() {
  wo.value = {
    reporter_id: auth.user?.userId ?? '',
    report_time: nowText(),
    report_method: 'web',
    urgency_level: 'normal'
  }
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/repair/workorder/${row.id}`)
  wo.value = data.data ?? { ...row }
  visible.value = true
}

async function save() {
  if (!wo.value) return
  if (!wo.value.device_id) {
    ElMessage.warning('请选择报修设备')
    return
  }
  if (!wo.value.fault_description) {
    ElMessage.warning('请填写故障描述')
    return
  }
  const { data } = await http.post('/repair/workorder', wo.value)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }
  wo.value = data.data
  visible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}

async function dispatch() {
  if (!wo.value?.id) return
  await http.post(`/repair/workorder/${wo.value.id}/dispatch`, { engineerId: wo.value.assigned_engineer_id })
  await openDetail(wo.value)
  crudRef.value?.load()
}

async function act(type: string) {
  if (!wo.value?.id) return
  if (type === 'accept') await http.post(`/repair/workorder/${wo.value.id}/accept`)
  if (type === 'complete') {
    await http.post(`/repair/workorder/${wo.value.id}/complete`, {
      solution_description: wo.value.solution_description ?? '维修完成',
      parts_cost: wo.value.parts_cost ?? 0,
      labor_cost: wo.value.labor_cost ?? 0,
      total_cost: wo.value.total_cost ?? 0
    })
  }
  if (type === 'verify') {
    await http.post(`/repair/workorder/${wo.value.id}/verify`, {
      verifier_id: wo.value.reporter_id,
      verify_result: wo.value.verify_result ?? 'pass',
      verify_comment: wo.value.verify_comment,
      satisfaction_rating: wo.value.satisfaction_rating ?? 5
    })
  }
  await openDetail(wo.value)
  crudRef.value?.load()
}
</script>

<style scoped>
.timeline-section {
  margin-top: 16px;
}
</style>
