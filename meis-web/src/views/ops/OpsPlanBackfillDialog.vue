<template>
  <el-dialog v-model="visible" title="执行补录" width="480px" destroy-on-close append-to-body @closed="onClosed">
    <el-form label-width="120px">
      <el-form-item label="执行日期" required>
        <el-date-picker
          v-model="form.planned_date"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          @change="onPlannedDateChange"
        />
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker
          v-model="form.execute_start_time"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker
          v-model="form.execute_end_time"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="下次执行日期">
        <div class="next-due-row">
          <el-checkbox v-model="form.update_next_due" @change="onUpdateNextDueChange">修改</el-checkbox>
          <el-date-picker
            v-model="form.next_due_date"
            type="date"
            value-format="YYYY-MM-DD"
            style="flex: 1"
            :disabled="!form.update_next_due"
            placeholder="不勾选则不变更计划下次到期"
          />
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { calcItemNextDueDate } from '@/utils/cycleDays'

const visible = ref(false)
const saving = ref(false)
const planId = ref<string>('')
const moduleApi = ref('maintain')
const planItemIds = ref<string[]>([])
const planSnapshot = ref<Record<string, unknown>>({})
let onDone: (() => void) | undefined

const form = reactive({
  planned_date: '' as string,
  execute_start_time: '' as string,
  execute_end_time: '' as string,
  update_next_due: false,
  next_due_date: '' as string
})

function todayStr() {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** 执行日期 → 起止默认全天 */
function applyDayBounds(date: string) {
  if (!date) return
  form.execute_start_time = `${date}T00:00:00`
  form.execute_end_time = `${date}T23:59:59`
}

function fillNextDueFromPlan() {
  if (!form.planned_date) {
    form.next_due_date = ''
    return
  }
  form.next_due_date = calcItemNextDueDate(planSnapshot.value, form.planned_date)
}

function onPlannedDateChange() {
  applyDayBounds(form.planned_date)
  if (form.update_next_due) fillNextDueFromPlan()
}

function onUpdateNextDueChange(checked: boolean | string | number) {
  if (checked) {
    fillNextDueFromPlan()
  } else {
    form.next_due_date = ''
  }
}

function open(opts: {
  module: 'maintain' | 'inspect' | 'pm'
  planId: string
  plan?: Record<string, unknown>
  planItemIds?: string[]
  onDone?: () => void
}) {
  moduleApi.value = opts.module
  planId.value = opts.planId
  planItemIds.value = opts.planItemIds ?? []
  planSnapshot.value = opts.plan ?? {}
  onDone = opts.onDone
  const d = todayStr()
  form.planned_date = d
  applyDayBounds(d)
  form.update_next_due = false
  form.next_due_date = ''
  visible.value = true
}

function onClosed() {
  saving.value = false
}

async function submit() {
  if (!form.planned_date) {
    ElMessage.warning('请填写执行日期')
    return
  }
  if (!form.execute_start_time || !form.execute_end_time) {
    ElMessage.warning('请填写开始/结束时间')
    return
  }
  if (form.update_next_due && !form.next_due_date) {
    ElMessage.warning('已勾选修改下次执行日期，请填写日期')
    return
  }
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      client: 'web',
      planned_date: form.planned_date,
      execute_start_time: form.execute_start_time,
      execute_end_time: form.execute_end_time,
      update_next_due: form.update_next_due,
      next_due_date: form.update_next_due ? form.next_due_date : null
    }
    if (planItemIds.value.length) body.plan_item_ids = planItemIds.value
    const { data } = await http.post(`/${moduleApi.value}/plan/${planId.value}/backfill-execution`, body)
    const no = data.data?.execution_no
    ElMessage.success(no ? `已创建补录执行单 ${no}` : '已创建补录执行单')
    visible.value = false
    onDone?.()
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.next-due-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
</style>
