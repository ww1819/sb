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
        <el-button v-if="showCreate" type="primary" @click="openCreate">新增报修</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="wo">
        <GroupedFormFields :table="config.table" :model="wo" :fields="formFields" />

        <FormSection v-if="wo.id && timelineData" title="工单时间轴" class="timeline-section">
          <div v-if="timelineData.summary" class="timeline-summary">
            <span>总停机 {{ fmtMin(timelineData.summary.downtimeMinutes) }}</span>
            <span>响应 {{ fmtMin(timelineData.summary.responseMinutes) }}</span>
            <span>维修 {{ fmtMin(timelineData.summary.repairMinutes) }}</span>
            <span>待验收 {{ fmtMin(timelineData.summary.pendingVerifyMinutes) }}</span>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="m in timelineData.milestones"
              :key="m.key"
              :timestamp="m.skipped ? '已跳过' : fmt(m.at)"
              :type="m.done ? (m.skipped ? 'info' : 'primary') : 'info'"
              placement="top"
            >
              <div>{{ m.label }}</div>
              <div v-if="m.skipReason" class="muted">{{ m.skipReason }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-if="timelineData.segments?.length" class="segments">
            <div class="seg-title">维修中明细</div>
            <div v-for="(s, i) in timelineData.segments" :key="i" class="seg-row">
              {{ s.subStatusLabel }} · {{ fmt(s.start) }} ~ {{ fmt(s.end) }} · {{ fmtMin(s.minutes) }}
              <span v-if="s.remark" class="muted">（{{ s.remark }}）</span>
            </div>
          </div>
          <el-collapse v-if="timelineData.events?.length" class="event-collapse">
            <el-collapse-item title="全部事件流水" name="events">
              <div v-for="e in timelineData.events" :key="String(e.id)" class="event-row">
                <span class="event-time">{{ fmt(e.created_at) }}</span>
                <span>{{ e.event_label || e.event_type }}</span>
                <span v-if="e.remark" class="muted">· {{ e.remark }}</span>
              </div>
            </el-collapse-item>
          </el-collapse>
        </FormSection>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <template v-if="wo?.id">
          <template v-if="pageMode === 'apply'">
            <el-button v-if="can('cancel')" type="danger" plain @click="doCancel">取消报修</el-button>
          </template>
          <template v-else-if="pageMode === 'verify'">
            <el-button v-if="can('verify')" type="success" @click="openVerify">验收</el-button>
          </template>
          <template v-else>
            <el-button v-if="can('dispatch')" @click="openDispatch">派工</el-button>
            <el-button v-if="can('start')" type="success" plain @click="doStartRepair">开始维修</el-button>
            <el-button v-if="can('accept')" @click="doAccept">接单</el-button>
            <el-button v-if="can('transfer')" @click="openTransfer">转派</el-button>
            <el-button v-if="can('sub')" @click="openSubStatus">子状态</el-button>
            <el-button v-if="can('complete')" type="warning" @click="openComplete">完工</el-button>
            <el-button v-if="can('suspend')" @click="doSuspend">挂起</el-button>
            <el-button v-if="can('resume')" @click="doResume">恢复</el-button>
            <el-button v-if="can('cancel')" type="danger" plain @click="doCancel">取消</el-button>
          </template>
        </template>
        <el-button v-if="editable && !wo?.id" type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>

    <AppModal v-model="dispatchVisible" title="派工 / 指派工程师" size="md">
      <el-form label-width="100px">
        <el-form-item label="工程师" required>
          <RefSelect v-model="actionForm.engineerId" link-table="engineer" placeholder="请选择工程师" />
        </el-form-item>
        <el-form-item label="派工并开工">
          <el-switch v-model="actionForm.startRepair" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" @click="doDispatch">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="transferVisible" title="转派工程师" size="md">
      <el-form label-width="120px">
        <el-form-item label="目标工程师" required>
          <RefSelect v-model="actionForm.engineerId" link-table="engineer" placeholder="请选择工程师" />
        </el-form-item>
        <el-form-item v-if="wo?.status === 'repairing'" label="保持维修中">
          <el-switch v-model="actionForm.keepRepairing" />
        </el-form-item>
        <el-form-item label="转派原因">
          <el-input v-model="actionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="doTransfer">确认转派</el-button>
      </template>
    </AppModal>

    <AppModal v-model="completeVisible" title="维修完工" size="md">
      <el-form label-width="100px">
        <el-form-item label="处理方案">
          <el-input v-model="actionForm.solution" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="完工方式">
          <el-radio-group v-model="actionForm.skipVerify">
            <el-radio :value="false">提交验收</el-radio>
            <el-radio :value="true">直接结案（跳过验收）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" @click="doComplete">确认完工</el-button>
      </template>
    </AppModal>

    <AppModal v-model="subVisible" title="更新维修子状态" size="md">
      <el-form label-width="100px">
        <el-form-item label="子状态" required>
          <el-select v-model="actionForm.subStatus" style="width: 100%">
            <el-option v-for="o in subOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" @click="doSubStatus">确认</el-button>
      </template>
    </AppModal>
    <AppModal v-model="verifyVisible" title="维修验收" size="md">
      <el-form label-width="100px">
        <el-form-item label="验收结果" required>
          <el-radio-group v-model="actionForm.verifyResult">
            <el-radio value="pass">通过</el-radio>
            <el-radio value="fail">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="actionForm.verifyComment" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="actionForm.verifyResult === 'pass'" label="满意度">
          <el-rate v-model="actionForm.satisfactionRating" :max="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitVerify">确认验收</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const route = useRoute()
const pageMode = computed(() => {
  const path = route.path
  if (path.endsWith('/apply')) return 'apply'
  if (path.endsWith('/handle')) return 'handle'
  if (path.endsWith('/verify')) return 'verify'
  return 'all'
})

const modeTitles: Record<string, string> = {
  apply: '报修申请',
  handle: '维修处理',
  verify: '维修验收',
  all: '维修工单'
}

const config = computed<PageConfig>(() => ({
  title: modeTitles[pageMode.value] ?? '维修工单',
  apiBase: '/repair',
  table: 'repair_workorder',
  listPageUrl: '/repair/workorder/page',
  listMode: pageMode.value === 'all' ? undefined : pageMode.value,
  listFilters: pageMode.value === 'all' ? [{ key: 'status', label: '状态', dictType: 'wo_status' }] : undefined
}))

const showCreate = computed(() => pageMode.value === 'apply' || pageMode.value === 'all')
const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const wo = ref<Record<string, unknown> | null>(null)
const timelineData = ref<{
  summary?: Record<string, number | string>
  milestones?: Array<Record<string, unknown>>
  segments?: Array<Record<string, unknown>>
  events?: Array<Record<string, unknown>>
} | null>(null)

const dispatchVisible = ref(false)
const transferVisible = ref(false)
const completeVisible = ref(false)
const subVisible = ref(false)
const verifyVisible = ref(false)
const actionForm = reactive({
  engineerId: '' as string,
  startRepair: false,
  keepRepairing: false,
  skipVerify: false,
  solution: '',
  remark: '',
  subStatus: 'internal',
  verifyResult: 'pass' as 'pass' | 'fail',
  verifyComment: '',
  satisfactionRating: 5
})

const subOptions = [
  { value: 'internal', label: '院内维修' },
  { value: 'external', label: '院外维修' },
  { value: 'waiting_parts', label: '等待配件' },
  { value: 'waiting_approval', label: '待审批' },
  { value: 'on_site', label: '已到场' },
  { value: 'diagnosing', label: '诊断中' },
  { value: 'testing', label: '调试中' }
]

const status = computed(() => String(wo.value?.status ?? ''))
const editable = computed(() => !wo.value?.id || status.value === 'reported')
const modalTitle = computed(() => {
  if (!wo.value?.id) return '维修工单 新增'
  return editable.value ? '维修工单 编辑' : '维修工单 详情'
})
const formFields = computed(() => {
  const fields = getSchema('repair_workorder')
  if (editable.value) return fields
  return fields.map((f) => ({ ...f, readonly: true }))
})

function can(action: string) {
  const s = status.value
  const mode = pageMode.value
  if (mode === 'apply') {
    return action === 'cancel' && ['reported', 'dispatching'].includes(s)
  }
  if (mode === 'verify') {
    return action === 'verify' && s === 'pending_verify'
  }
  if (mode === 'handle' && action === 'verify') return false
  switch (action) {
    case 'dispatch':
      return ['reported', 'dispatching', 'pending_accept', 'accepted', 'repairing'].includes(s)
    case 'start':
      return ['reported', 'dispatching', 'pending_accept', 'accepted'].includes(s)
    case 'accept':
      return ['pending_accept', 'dispatching'].includes(s)
    case 'transfer':
      return ['dispatching', 'pending_accept', 'accepted', 'repairing'].includes(s)
    case 'sub':
      return s === 'repairing'
    case 'complete':
      return s === 'repairing'
    case 'verify':
      return s === 'pending_verify'
    case 'suspend':
      return s === 'repairing'
    case 'resume':
      return s === 'suspended'
    case 'cancel':
      return !['closed', 'cancelled', 'verified'].includes(s)
    default:
      return false
  }
}

function fmt(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

function fmtMin(v: unknown) {
  const n = Number(v ?? 0)
  if (!n) return '0m'
  if (n < 60) return `${n}m`
  const h = Math.floor(n / 60)
  const m = n % 60
  return m ? `${h}h${m}m` : `${h}h`
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
  timelineData.value = null
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/repair/workorder/${row.id}`)
  wo.value = data.data ?? { ...row }
  visible.value = true
  await loadTimeline()
}

async function loadTimeline() {
  if (!wo.value?.id) {
    timelineData.value = null
    return
  }
  const { data } = await http.get(`/repair/workorder/${wo.value.id}/timeline`)
  timelineData.value = data.data ?? null
}

async function refresh() {
  if (wo.value?.id) await openDetail(wo.value)
  crudRef.value?.load()
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
  ElMessage.success('报修成功')
  visible.value = false
  crudRef.value?.load()
}

function resetActionForm() {
  actionForm.engineerId = String(wo.value?.assigned_engineer_id ?? '')
  actionForm.startRepair = false
  actionForm.keepRepairing = false
  actionForm.skipVerify = false
  actionForm.solution = String(wo.value?.solution_description ?? '维修完成')
  actionForm.remark = ''
  actionForm.subStatus = String(wo.value?.repair_sub_status ?? 'internal')
}

function openDispatch() {
  resetActionForm()
  dispatchVisible.value = true
}

function openTransfer() {
  resetActionForm()
  actionForm.engineerId = ''
  transferVisible.value = true
}

function openComplete() {
  resetActionForm()
  completeVisible.value = true
}

function openSubStatus() {
  resetActionForm()
  subVisible.value = true
}

async function doDispatch() {
  if (!wo.value?.id || !actionForm.engineerId) {
    ElMessage.warning('请选择工程师')
    return
  }
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/dispatch`, {
    engineerId: actionForm.engineerId,
    startRepair: actionForm.startRepair,
    remark: actionForm.remark
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '派工失败')
    return
  }
  dispatchVisible.value = false
  ElMessage.success('派工成功')
  await refresh()
}

async function doStartRepair() {
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/start-repair`, {
    repair_sub_status: 'internal'
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '操作失败')
    return
  }
  ElMessage.success('已开始维修')
  await refresh()
}

async function doAccept() {
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/accept`, { startRepair: true })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '接单失败')
    return
  }
  ElMessage.success('接单成功')
  await refresh()
}

async function doTransfer() {
  if (!wo.value?.id || !actionForm.engineerId) {
    ElMessage.warning('请选择目标工程师')
    return
  }
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/transfer`, {
    engineerId: actionForm.engineerId,
    keepRepairing: actionForm.keepRepairing,
    remark: actionForm.remark
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '转派失败')
    return
  }
  transferVisible.value = false
  ElMessage.success('转派成功')
  await refresh()
}

async function doSubStatus() {
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/sub-status`, {
    repair_sub_status: actionForm.subStatus,
    remark: actionForm.remark
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '更新失败')
    return
  }
  subVisible.value = false
  ElMessage.success('子状态已更新')
  await refresh()
}

async function doComplete() {
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/complete`, {
    solution_description: actionForm.solution || '维修完成',
    parts_cost: wo.value.parts_cost ?? 0,
    labor_cost: wo.value.labor_cost ?? 0,
    total_cost: wo.value.total_cost ?? 0,
    skipVerify: actionForm.skipVerify
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '完工失败')
    return
  }
  completeVisible.value = false
  ElMessage.success(actionForm.skipVerify ? '已结案' : '已提交验收')
  await refresh()
}

function openVerify() {
  actionForm.verifyResult = 'pass'
  actionForm.verifyComment = '验收通过'
  actionForm.satisfactionRating = 5
  verifyVisible.value = true
}

async function submitVerify() {
  if (!wo.value?.id) return
  const result = actionForm.verifyResult
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/verify`, {
    verifier_id: auth.user?.userId ?? wo.value.reporter_id,
    verify_result: result,
    verify_comment: actionForm.verifyComment || (result === 'pass' ? '验收通过' : '验收不通过'),
    satisfaction_rating: result === 'pass' ? actionForm.satisfactionRating : null
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '验收失败')
    return
  }
  verifyVisible.value = false
  ElMessage.success(result === 'pass' ? '验收通过' : '已退回维修')
  await refresh()
}

async function doVerify(result: 'pass' | 'fail') {
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/verify`, {
    verifier_id: auth.user?.userId ?? wo.value.reporter_id,
    verify_result: result,
    verify_comment: result === 'pass' ? '验收通过' : '验收不通过，需返修',
    satisfaction_rating: result === 'pass' ? 5 : null
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '验收失败')
    return
  }
  ElMessage.success(result === 'pass' ? '验收通过' : '已退回维修')
  await refresh()
}

async function doSuspend() {
  if (!wo.value?.id) return
  const { value } = await ElMessageBox.prompt('请输入挂起原因', '挂起工单', { inputPlaceholder: '原因' })
  await http.post(`/repair/workorder/${wo.value.id}/suspend`, { remark: value })
  ElMessage.success('已挂起')
  await refresh()
}

async function doResume() {
  if (!wo.value?.id) return
  await http.post(`/repair/workorder/${wo.value.id}/resume`, { repair_sub_status: 'internal' })
  ElMessage.success('已恢复')
  await refresh()
}

async function doCancel() {
  if (!wo.value?.id) return
  await ElMessageBox.confirm('确认取消该工单？设备将恢复可用状态。', '取消工单', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/cancel`, { remark: '用户取消' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '取消失败')
    return
  }
  ElMessage.success('已取消')
  await refresh()
}
</script>

<style scoped>
.timeline-section {
  margin-top: 16px;
}
.timeline-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.segments {
  margin: 8px 0 12px;
  padding-left: 8px;
}
.seg-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.seg-row,
.event-row {
  font-size: 13px;
  line-height: 1.7;
}
.event-collapse {
  margin-top: 8px;
}
.event-time {
  display: inline-block;
  min-width: 140px;
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
</style>
