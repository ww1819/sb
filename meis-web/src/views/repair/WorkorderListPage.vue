<template>
  <div class="workflow-crud">
    <CrudPage
      ref="crudRef"
      :config="config"
      detail-mode
      hide-add
      delete-url="/repair/workorder"
      :operation-column-width="400"
      :can-edit="canEditRow"
      :can-delete="canDeleteRow"
      @detail="openDetail"
      @add="openCreate"
    >
      <template #toolbar-extra>
        <el-button v-if="showCreate" type="primary" @click="openCreate">新增报修</el-button>
      </template>
      <template #row-actions="{ row }">
        <template v-if="pageMode === 'apply' || pageMode === 'all'">
          <el-button v-if="canRowSubmit(row)" link type="primary" @click.stop="doSubmit(row)">提交</el-button>
          <el-button v-if="canRowWithdraw(row)" link type="warning" @click.stop="doWithdraw(row)">撤回</el-button>
        </template>
        <template v-if="pageMode === 'handle' || pageMode === 'all'">
          <el-button v-if="canOnRow('dispatch', row)" link type="primary" @click.stop="openDispatch(row)">派工</el-button>
          <el-button v-if="canOnRow('segment', row)" link type="primary" @click.stop="openAddSegment(row)">添加进程</el-button>
          <el-button v-if="canOnRow('cancel', row)" link type="danger" @click.stop="doCancel(row)">取消</el-button>
        </template>
        <el-button v-if="canRowChangeLog(row)" link @click.stop="openChangeLog(row)">变更记录</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="wo">
        <GroupedFormFields :table="config.table" :model="wo" :fields="formFields" />

        <FormSection v-if="wo.id && pageMode !== 'apply'" title="维修进程段" class="timeline-section">
          <div v-if="!processSegments.length" class="muted">
            暂无进程段。
            <span v-if="!isRepairEngineer">添加进程需当前登录账号为维修工程师（用户管理开启「是否维修工程师」），且为工单负责人或待派单可首段。</span>
          </div>
          <div v-for="seg in processSegments" :key="String(seg.id)" class="seg-row">
            <div>
              <strong>{{ seg.type_name }}</strong>
              <el-tag v-if="seg.open" size="small" type="success" style="margin-left: 8px">进行中</el-tag>
              <span v-if="seg.auto_created" class="muted"> · 系统自动</span>
            </div>
            <div class="muted">
              {{ fmt(seg.started_at) }} ~ {{ seg.ended_at ? fmt(seg.ended_at) : '至今' }}
              <span v-if="seg.user_name"> · {{ seg.user_name }}</span>
            </div>
            <div v-if="seg.remark || seg.verify_comment" class="muted">{{ seg.remark || seg.verify_comment }}</div>
            <div v-if="seg.parts?.length" class="seg-parts">
              <div v-for="p in seg.parts" :key="String(p.id)" class="muted">
                配件：{{ p.part_name || p.spare_part_id }} × {{ p.quantity }}
                <span v-if="p.unit_price"> @ {{ p.unit_price }}</span>
              </div>
            </div>
            <el-button
              v-if="seg.open && seg.can_add_parts && can('segment')"
              text
              type="primary"
              size="small"
              @click="openAddPart(seg)"
            >添加配件</el-button>
          </div>
        </FormSection>

        <FormSection v-if="wo.id && timelineData && pageMode !== 'apply'" title="工单时间轴" class="timeline-section">
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
        <template v-if="wo?.id && (pageMode === 'handle' || pageMode === 'all')">
          <el-button v-if="can('grab')" type="primary" @click="doGrab">抢单</el-button>
          <el-button v-if="can('start')" type="success" plain @click="doStartRepair">开始维修</el-button>
          <el-button v-if="can('accept')" @click="doAccept">接单</el-button>
          <el-button v-if="can('segment')" type="primary" plain @click="openAddSegment()">添加进程</el-button>
          <el-button v-if="can('transfer')" @click="openTransfer">转派</el-button>
          <el-button v-if="can('sub')" @click="openSubStatus">子状态</el-button>
          <el-button v-if="can('complete')" type="warning" @click="openComplete">完工</el-button>
          <el-button v-if="can('suspend')" @click="doSuspend">挂起</el-button>
          <el-button v-if="can('resume')" @click="doResume">恢复</el-button>
        </template>
        <template v-if="pageMode === 'verify' && wo?.id">
          <el-button v-if="can('verify')" type="success" @click="openVerify">验收</el-button>
        </template>
        <el-button v-if="editable && !wo?.id" type="primary" @click="saveDraft">保存草稿</el-button>
        <el-button v-if="editable && wo?.id && status === 'draft'" type="primary" plain @click="saveDraft">保存</el-button>
      </template>
    </AppModal>

    <EntityChangeHistoryDrawer
      v-model="changeLogVisible"
      entity-type="repair_workorder"
      :entity-id="changeLogEntityId"
    />

    <AppModal v-model="segmentVisible" title="添加维修进程" size="md">
      <el-form label-width="100px">
        <el-form-item label="进程类型" required>
          <el-select v-model="actionForm.processTypeId" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in addableTypes" :key="String(t.id)" :label="String(t.type_name)" :value="String(t.id)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedAddableType?.can_add_parts" label="配件">
          <div v-for="(row, idx) in segmentPartRows" :key="idx" class="seg-part-form-row">
            <RefSelect v-model="row.sparePartId" link-table="spare_part" placeholder="配件" style="flex: 1" />
            <el-input-number v-model="row.quantity" :min="1" :max="9999" style="width: 120px" />
            <el-button text type="danger" @click="segmentPartRows.splice(idx, 1)">删</el-button>
          </div>
          <el-button text type="primary" @click="segmentPartRows.push({ sparePartId: '', quantity: 1 })">+ 添加配件</el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.segmentRemark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="segmentVisible = false">取消</el-button>
        <el-button type="primary" @click="doAddSegment">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="partVisible" title="进程段添加配件" size="md">
      <el-form label-width="80px">
        <el-form-item label="配件" required>
          <RefSelect v-model="partForm.sparePartId" link-table="spare_part" placeholder="请选择配件" />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="partForm.quantity" :min="1" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partVisible = false">取消</el-button>
        <el-button type="primary" @click="doAddPart">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="dispatchVisible" title="派工 / 指派工程师" size="md">
      <el-form label-width="100px">
        <el-form-item label="工程师" required>
          <RefSelect v-model="actionForm.userId" link-table="repair_engineer" placeholder="请选择工程师" />
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
          <RefSelect v-model="actionForm.userId" link-table="repair_engineer" placeholder="请选择工程师" />
        </el-form-item>
        <el-form-item v-if="wo?.status === 'repairing' || wo?.status === 'verify_rejected'" label="保持当前状态">
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
            <el-radio v-if="status !== 'verify_rejected'" :value="true">直接结案（跳过验收）</el-radio>
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
        <el-form-item v-if="actionForm.verifyResult === 'fail'" label="拒绝原因" required>
          <el-input v-model="actionForm.verifyComment" type="textarea" :rows="3" placeholder="请填写拒绝验收原因" />
        </el-form-item>
        <el-form-item v-else label="验收意见">
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
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'
import type { PageConfig } from '@/config/pageRegistry'
import {
  REPAIR_APPLY_FILTERS,
  REPAIR_HANDLE_FILTERS,
  REPAIR_VERIFY_FILTERS
} from '@/config/repairListFilters'
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

const repairListFilters = computed(() => {
  switch (pageMode.value) {
    case 'apply':
      return REPAIR_APPLY_FILTERS
    case 'handle':
      return REPAIR_HANDLE_FILTERS
    case 'verify':
      return REPAIR_VERIFY_FILTERS
    default:
      return REPAIR_APPLY_FILTERS
  }
})

const config = computed<PageConfig>(() => ({
  title: modeTitles[pageMode.value] ?? '维修工单',
  apiBase: '/repair',
  table: 'repair_workorder',
  listPageUrl: '/repair/workorder/page',
  listMode: pageMode.value === 'all' ? undefined : pageMode.value,
  listFilters: pageMode.value === 'all' ? REPAIR_APPLY_FILTERS : repairListFilters.value
}))

const showCreate = computed(() => pageMode.value === 'apply' || pageMode.value === 'all')
const isRepairEngineer = ref(false)
const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const changeLogVisible = ref(false)
const changeLogEntityId = ref('')
const wo = ref<Record<string, unknown> | null>(null)
const timelineData = ref<{
  summary?: Record<string, number | string>
  milestones?: Array<Record<string, unknown>>
  segments?: Array<Record<string, unknown>>
  events?: Array<Record<string, unknown>>
} | null>(null)

const processSegments = ref<Array<Record<string, unknown>>>([])
const addableTypes = ref<Array<Record<string, unknown>>>([])
const segmentVisible = ref(false)
const partVisible = ref(false)
const partForm = reactive({ segmentId: '', sparePartId: '', quantity: 1 })
const segmentPartRows = ref<Array<{ sparePartId: string; quantity: number }>>([])
const dispatchVisible = ref(false)
const transferVisible = ref(false)
const completeVisible = ref(false)
const subVisible = ref(false)
const verifyVisible = ref(false)
const actionForm = reactive({
  userId: '' as string,
  startRepair: false,
  keepRepairing: false,
  skipVerify: false,
  solution: '',
  remark: '',
  segmentRemark: '',
  processTypeId: '',
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
const editable = computed(() => !wo.value?.id || status.value === 'draft')
const selectedAddableType = computed(() =>
  addableTypes.value.find((t) => String(t.id) === actionForm.processTypeId)
)

function rowStatus(row: Record<string, unknown>) {
  return String(row.status ?? '')
}

function isCancelledStatus(s: string) {
  return s === 'cancelled'
}

function isHandleReadOnlyStatus(s: string) {
  return ['pending_verify', 'verified', 'closed'].includes(s)
}

function isApplyScope() {
  return pageMode.value === 'apply' || pageMode.value === 'all'
}

function canWithdrawRow(row: Record<string, unknown>) {
  if (isCancelledStatus(rowStatus(row))) return false
  if (rowStatus(row) !== 'reported') return false
  return !row.assigned_user_id && !row.dispatch_started_at && !row.assigned_at
    && !row.accepted_at && !row.repair_start_time && !row.response_time
}

function canEditRow(row: Record<string, unknown>) {
  return rowStatus(row) === 'draft'
}

function canDeleteRow(row: Record<string, unknown>) {
  return rowStatus(row) === 'draft' && isApplyScope()
}

function canRowSubmit(row: Record<string, unknown>) {
  return rowStatus(row) === 'draft' && isApplyScope()
}

function canRowWithdraw(row: Record<string, unknown>) {
  return canWithdrawRow(row) && isApplyScope()
}

function canRowChangeLog(row: Record<string, unknown>) {
  return Boolean(row.id)
}

const APPLY_FORM_GROUPS = new Set(['basic', 'remark'])

const canWithdraw = computed(() => canWithdrawRow(wo.value ?? {}))
const modalTitle = computed(() => {
  if (!wo.value?.id) return '维修工单 新增'
  if (status.value === 'draft') return '维修工单 草稿'
  return editable.value ? '维修工单 编辑' : '维修工单 详情'
})
const formFields = computed(() => {
  let fields = getSchema('repair_workorder')
  if (pageMode.value === 'apply') {
    fields = fields.filter((f) => APPLY_FORM_GROUPS.has(f.group ?? 'basic'))
  }
  if (editable.value) return fields
  return fields.map((f) => ({ ...f, readonly: true }))
})

function isUnassignedWo(row?: Record<string, unknown> | null) {
  const target = row ?? wo.value
  if (!target) return true
  const id = target.assigned_user_id
  return id == null || String(id).trim() === ''
}

function isOwnerWo(row?: Record<string, unknown> | null) {
  const target = row ?? wo.value
  const uid = auth.user?.userId
  if (!target || !uid) return false
  return String(target.assigned_user_id ?? '') === String(uid)
}

function can(action: string, row?: Record<string, unknown> | null) {
  const target = row ?? wo.value
  const s = String(target?.status ?? status.value)
  const mode = pageMode.value
  if (isCancelledStatus(s)) return false
  if (action === 'submit') return s === 'draft' && (mode === 'apply' || mode === 'all')
  if (action === 'withdraw') {
    return canWithdrawRow(target ?? {}) && (mode === 'apply' || mode === 'all')
  }
  if (action === 'delete') return s === 'draft' && (mode === 'apply' || mode === 'all')
  if (mode === 'apply') return false
  if (mode === 'verify') {
    return action === 'verify' && s === 'pending_verify'
  }
  if (mode === 'handle' && isHandleReadOnlyStatus(s)) return false
  if (mode === 'handle' && action === 'verify') return false

  const unassigned = isUnassignedWo(target)
  const owner = isOwnerWo(target)

  switch (action) {
    case 'grab':
      return isRepairEngineer.value
        && (s === 'reported' || s === 'dispatching')
        && unassigned
    case 'dispatch':
      if (s === 'reported' || s === 'dispatching') return unassigned
      return ['pending_accept', 'accepted', 'repairing'].includes(s)
    case 'start':
      return ['pending_accept', 'accepted'].includes(s) && owner
    case 'accept':
      return ['pending_accept', 'dispatching'].includes(s) && owner && !unassigned
    case 'segment':
      if (!isRepairEngineer.value) return false
      if (!['reported', 'dispatching', 'pending_accept', 'accepted', 'repairing', 'suspended', 'verify_rejected'].includes(s)) return false
      if (unassigned && (s === 'reported' || s === 'dispatching')) return true
      return owner
    case 'transfer':
      return ['dispatching', 'pending_accept', 'accepted', 'repairing', 'verify_rejected'].includes(s) && owner
    case 'sub':
      return (s === 'repairing' || s === 'verify_rejected') && owner
    case 'complete':
      return (s === 'repairing' || s === 'verify_rejected') && owner
    case 'verify':
      return s === 'pending_verify'
    case 'suspend':
      return s === 'repairing' && owner
    case 'resume':
      return s === 'suspended' && owner
    case 'cancel':
      return mode === 'handle' || mode === 'all'
        ? !['draft', 'closed', 'cancelled', 'verified', 'pending_verify'].includes(s)
        : !['draft', 'closed', 'cancelled', 'verified'].includes(s)
    default:
      return false
  }
}

function canOnRow(action: string, row: Record<string, unknown>) {
  return can(action, row)
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
  processSegments.value = []
  visible.value = true
}

async function loadSegments() {
  if (!wo.value?.id || pageMode.value === 'apply') {
    processSegments.value = []
    return
  }
  const { data } = await http.get(`/repair/workorder/${wo.value.id}/segments`)
  processSegments.value = data.data ?? []
}

async function loadAddableTypes() {
  if (!wo.value?.id) return
  const { data } = await http.get('/repair/process-type/addable', {
    params: { workorderId: wo.value.id, status: status.value }
  })
  addableTypes.value = data.data ?? []
}

async function openAddSegment(row?: Record<string, unknown>) {
  if (row?.id) {
    await openDetail(row)
  }
  if (!wo.value?.id) return
  if (!can('segment')) {
    ElMessage.warning(
      isRepairEngineer.value
        ? '当前账号不是该工单负责人，无法添加进程'
        : '添加维修进程需当前登录账号为「维修工程师」（请在用户管理开启）'
    )
    return
  }
  actionForm.processTypeId = ''
  actionForm.segmentRemark = ''
  segmentPartRows.value = []
  await loadAddableTypes()
  segmentVisible.value = true
}

async function doAddSegment() {
  if (!wo.value?.id || !actionForm.processTypeId) {
    ElMessage.warning('请选择进程类型')
    return
  }
  const parts = segmentPartRows.value
    .filter((r) => r.sparePartId)
    .map((r) => ({ spare_part_id: r.sparePartId, quantity: r.quantity }))
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/segments`, {
    processTypeId: actionForm.processTypeId,
    remark: actionForm.segmentRemark,
    parts
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '添加失败')
    return
  }
  segmentVisible.value = false
  ElMessage.success('进程段已添加')
  await refresh()
}

function openAddPart(seg: Record<string, unknown>) {
  partForm.segmentId = String(seg.id)
  partForm.sparePartId = ''
  partForm.quantity = 1
  partVisible.value = true
}

async function doAddPart() {
  if (!wo.value?.id || !partForm.segmentId || !partForm.sparePartId) {
    ElMessage.warning('请选择配件')
    return
  }
  const { data } = await http.post(
    `/repair/workorder/${wo.value.id}/segments/${partForm.segmentId}/parts`,
    { spare_part_id: partForm.sparePartId, quantity: partForm.quantity }
  )
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '添加失败')
    return
  }
  partVisible.value = false
  ElMessage.success('配件已添加')
  await loadSegments()
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/repair/workorder/${row.id}`)
  wo.value = data.data ?? { ...row }
  visible.value = true
  await Promise.all([loadTimeline(), loadSegments()])
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

async function saveDraft() {
  if (!wo.value) return
  if (!wo.value.device_id) {
    ElMessage.warning('请选择报修设备')
    return
  }
  if (!wo.value.fault_description) {
    ElMessage.warning('请填写故障描述')
    return
  }
  const id = wo.value.id
  const { data } = id
    ? await http.put(`/repair/workorder/${id}`, wo.value)
    : await http.post('/repair/workorder', wo.value)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }
  wo.value = data.data
  ElMessage.success(id ? '草稿已保存' : '草稿已创建')
  await loadTimeline()
  crudRef.value?.load()
}

async function doSubmit(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target) return
  if (!target.id) {
    wo.value = { ...target }
    await saveDraft()
    if (!wo.value?.id) return
    return doSubmit(wo.value)
  }
  await ElMessageBox.confirm('提交后将进入维修流程，提交前请确认信息无误。', '提交报修', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${target.id}/submit`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '提交失败')
    return
  }
  ElMessage.success('已提交')
  if (!row) visible.value = false
  crudRef.value?.load()
}

async function doWithdraw(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target?.id) return
  await ElMessageBox.confirm('撤回后将回到草稿，可再次修改并提交。设备将恢复可用。', '撤回报修', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${target.id}/withdraw`, { remark: '用户撤回' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '撤回失败')
    return
  }
  ElMessage.success('已撤回为草稿')
  if (row) {
    crudRef.value?.load()
    return
  }
  await refresh()
}

async function doDelete(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target?.id) return
  await ElMessageBox.confirm('确认删除该草稿报修单？', '删除', { type: 'warning' })
  const { data } = await http.delete(`/repair/workorder/${target.id}`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '删除失败')
    return
  }
  ElMessage.success('已删除')
  if (!row) visible.value = false
  crudRef.value?.load()
}

function openChangeLog(row?: Record<string, unknown>) {
  const id = row?.id ?? wo.value?.id
  if (!id) return
  changeLogEntityId.value = String(id)
  changeLogVisible.value = true
}

function resetActionForm() {
  actionForm.userId = String(wo.value?.assigned_user_id ?? '')
  actionForm.startRepair = false
  actionForm.keepRepairing = false
  actionForm.skipVerify = false
  actionForm.solution = String(wo.value?.solution_description ?? '维修完成')
  actionForm.remark = ''
  actionForm.subStatus = String(wo.value?.repair_sub_status ?? 'internal')
}

function openDispatch(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  resetActionForm()
  dispatchVisible.value = true
}

function openTransfer() {
  resetActionForm()
  actionForm.userId = ''
  transferVisible.value = true
}

function openComplete() {
  resetActionForm()
  if (status.value === 'verify_rejected') {
    actionForm.skipVerify = false
  }
  completeVisible.value = true
}

function openSubStatus() {
  resetActionForm()
  subVisible.value = true
}

async function doDispatch() {
  if (!wo.value?.id || !actionForm.userId) {
    ElMessage.warning('请选择工程师')
    return
  }
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/dispatch`, {
    userId: actionForm.userId,
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

async function doGrab() {
  if (!wo.value?.id) return
  await ElMessageBox.confirm('确认抢单？抢单后您将成为负责人并直接开始维修。', '抢单', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/grab`, { remark: '工程师抢单' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '抢单失败')
    return
  }
  ElMessage.success('抢单成功，已开始维修')
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
  if (!wo.value?.id || !actionForm.userId) {
    ElMessage.warning('请选择目标工程师')
    return
  }
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/transfer`, {
    userId: actionForm.userId,
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
  if (result === 'fail' && !actionForm.verifyComment.trim()) {
    ElMessage.warning('请填写拒绝验收原因')
    return
  }
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
  ElMessage.success(result === 'pass' ? '验收通过' : '已拒绝验收，工单退回返修')
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
  ElMessage.success(result === 'pass' ? '验收通过' : '已拒绝验收，工单退回返修')
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

async function doCancel(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target?.id) return
  await ElMessageBox.confirm('确认取消该工单？设备将恢复可用状态。', '取消工单', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${target.id}/cancel`, { remark: '用户取消' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '取消失败')
    return
  }
  ElMessage.success('已取消')
  if (row) {
    crudRef.value?.load()
    return
  }
  await refresh()
}

async function loadMyEngineerFlag() {
  if (pageMode.value !== 'handle' && pageMode.value !== 'all') {
    isRepairEngineer.value = false
    return
  }
  try {
    const { data } = await http.get('/repair/engineer/me')
    isRepairEngineer.value = Boolean(data.data?.isRepairEngineer)
  } catch {
    isRepairEngineer.value = false
  }
}

onMounted(() => {
  void loadMyEngineerFlag()
})
onActivated(() => {
  void loadMyEngineerFlag()
})
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
.seg-parts {
  margin-top: 4px;
  padding-left: 8px;
}
.seg-part-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.event-time {
  display: inline-block;
  min-width: 140px;
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
</style>
