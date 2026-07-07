<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail">
      <template #toolbar-extra>
        <el-button v-if="project?.id && project.approval_status === 'draft'" @click="submit">提交项目审批</el-button>
        <el-button v-if="project?.id && project.status === 'draft' && project.approval_status === 'approved'" @click="transition('bidding')">启动招标</el-button>
        <el-button v-if="project?.id && project.status === 'bidding'" @click="transition('awarded')">定标</el-button>
        <el-button v-if="project?.id && project.status === 'awarded'" type="success" @click="createContract">生成合同</el-button>
        <el-button v-if="project?.id" @click="printDoc">打印</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" title="采购项目 详情" size="xl">
      <template v-if="project">
        <el-steps :active="stepIndex" finish-status="success" simple class="project-steps">
          <el-step title="草稿" /><el-step title="审批" /><el-step title="招标" /><el-step title="定标" /><el-step title="关闭" />
        </el-steps>
        <el-tabs>
          <el-tab-pane label="项目信息">
            <GroupedFormFields :table="config.table" :model="project" />
          </el-tab-pane>
          <el-tab-pane label="投标人">
            <el-table :data="bidders" border size="small" max-height="280">
              <el-table-column label="投标人" min-width="140">
                <template #default="{ row }"><el-input v-model="row.bidder_name" size="small" /></template>
              </el-table-column>
              <el-table-column label="报价" width="120">
                <template #default="{ row }"><el-input-number v-model="row.bid_amount" :min="0" size="small" style="width:100%" /></template>
              </el-table-column>
              <el-table-column label="联系人" width="100">
                <template #default="{ row }"><el-input v-model="row.contact_person" size="small" /></template>
              </el-table-column>
              <el-table-column label="中标" width="70">
                <template #default="{ row }"><el-switch v-model="row.is_winner" size="small" /></template>
              </el-table-column>
              <el-table-column label="标书" min-width="140">
                <template #default="{ row }">
                  <FieldRenderer v-model="row.bid_doc_url" :field="{ prop: 'bid_doc_url', label: '标书', type: 'file' }" />
                </template>
              </el-table-column>
              <el-table-column width="60">
                <template #default="{ $index }"><el-button link type="danger" @click="bidders.splice($index, 1)">删</el-button></template>
              </el-table-column>
            </el-table>
            <el-button class="mt-8" @click="bidders.push({ bidder_name: '', bid_amount: 0, is_winner: false })">添加投标人</el-button>
          </el-tab-pane>
          <el-tab-pane label="质疑投诉">
            <el-table :data="complaints" border size="small" max-height="280">
              <el-table-column label="日期" width="140">
                <template #default="{ row }"><el-date-picker v-model="row.complaint_date" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" /></template>
              </el-table-column>
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-select v-model="row.complaint_type" size="small"><el-option label="质疑" value="query" /><el-option label="投诉" value="complaint" /></el-select>
                </template>
              </el-table-column>
              <el-table-column label="内容" min-width="160">
                <template #default="{ row }"><el-input v-model="row.content" type="textarea" :rows="2" /></template>
              </el-table-column>
              <el-table-column label="附件" min-width="140">
                <template #default="{ row }">
                  <FieldRenderer v-model="row.attachment_url" :field="{ prop: 'attachment_url', label: '附件', type: 'file' }" />
                </template>
              </el-table-column>
              <el-table-column width="60">
                <template #default="{ $index }"><el-button link type="danger" @click="complaints.splice($index, 1)">删</el-button></template>
              </el-table-column>
            </el-table>
            <el-button class="mt-8" @click="complaints.push({ complaint_type: 'query', status: 'open' })">添加记录</el-button>
          </el-tab-pane>
          <el-tab-pane label="招标时间轴">
            <el-timeline v-if="events.length">
              <el-timeline-item v-for="(ev, i) in events" :key="i" :timestamp="String(ev.event_date ?? '')">
                {{ ev.event_title }} <span v-if="ev.event_desc" class="muted">— {{ ev.event_desc }}</span>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无招标事件，状态流转后自动记录" />
            <el-divider />
            <el-button @click="events.push({ event_type: 'notice', event_date: '', event_title: '' })">添加事件</el-button>
            <div v-for="(ev, i) in events" :key="'e'+i" class="event-row">
              <el-select v-model="ev.event_type" size="small" style="width:120px">
                <el-option label="公告" value="notice" /><el-option label="开标" value="bid_open" />
                <el-option label="评标" value="evaluation" /><el-option label="定标" value="award" /><el-option label="签约" value="contract" />
              </el-select>
              <el-date-picker v-model="ev.event_date" type="date" value-format="YYYY-MM-DD" size="small" />
              <el-input v-model="ev.event_title" placeholder="事件标题" size="small" style="flex:1" />
              <el-button link type="danger" @click="events.splice(i, 1)">删</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
        <ApprovalPanel v-if="project.id" business-type="purchase_project" :business-id="String(project.id)" @changed="reload" />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="save">保存</el-button>
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
import FieldRenderer from '@/components/FieldRenderer.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printProjectDoc } from '@/utils/printDoc'

const auth = useAuthStore()
const config = getPageConfig('/purchase/project')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const project = ref<Record<string, unknown> | null>(null)
const bidders = ref<Record<string, unknown>[]>([])
const complaints = ref<Record<string, unknown>[]>([])
const events = ref<Record<string, unknown>[]>([])

const stepIndex = computed(() => {
  const p = project.value
  if (!p) return 1
  const status = String(p.status ?? 'draft')
  if (status === 'closed') return 5
  if (status === 'awarded') return 4
  if (status === 'bidding') return 3
  if (p.approval_status === 'approved') return 2
  return 1
})

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/purchase/project/${row.id}`)
  project.value = data.data
  bidders.value = (data.data?.bidders as Record<string, unknown>[]) ?? []
  complaints.value = (data.data?.complaints as Record<string, unknown>[]) ?? []
  events.value = (data.data?.events as Record<string, unknown>[]) ?? []
  visible.value = true
}

async function save() {
  if (!project.value) return
  await http.post('/purchase/project', {
    ...project.value,
    bidders: bidders.value,
    complaints: complaints.value,
    events: events.value
  })
  ElMessage.success('已保存')
  visible.value = false
  crudRef.value?.load()
}

async function submit() {
  if (!project.value?.id) return
  await http.post(`/purchase/project/${project.value.id}/submit`, { applicantId: auth.user?.userId })
  ElMessage.success('已提交项目审批')
  await reload()
}

async function transition(status: string) {
  if (!project.value?.id) return
  try {
    await http.post(`/purchase/project/${project.value.id}/transition`, { status })
    await reload()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '状态流转失败')
  }
}

async function createContract() {
  if (!project.value?.id) return
  const { data } = await http.post(`/purchase/project/${project.value.id}/create-contract`)
  ElMessage.success(`已生成合同：${data.data?.contract_code ?? ''}`)
}

async function reload() {
  if (!project.value?.id) return
  const { data } = await http.get(`/purchase/project/${project.value.id}`)
  project.value = data.data
  bidders.value = (data.data?.bidders as Record<string, unknown>[]) ?? []
  complaints.value = (data.data?.complaints as Record<string, unknown>[]) ?? []
  events.value = (data.data?.events as Record<string, unknown>[]) ?? []
  crudRef.value?.load()
}

async function printDoc() {
  if (!project.value?.id) return
  const { data } = await http.get(`/purchase/project/${project.value.id}`)
  if (data.code === 0 && data.data) printProjectDoc(data.data)
}
</script>

<style scoped>
.project-steps { margin: 8px 0 12px; }
.mt-8 { margin-top: 8px; }
.event-row { display: flex; gap: 8px; align-items: center; margin-top: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
