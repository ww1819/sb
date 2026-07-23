<template>
  <FormSection title="纳入申请" class="include-section">
    <div class="include-toolbar">
      <el-button size="small" @click="load">刷新</el-button>
      <el-button v-if="canInitiate" type="primary" size="small" @click="openApply">发起纳入</el-button>
    </div>
    <el-table :data="rows" border size="small" empty-text="暂无纳入申请">
      <el-table-column prop="device_code" label="设备编码" width="110" />
      <el-table-column prop="device_name" label="设备名称" min-width="120" />
      <el-table-column prop="applicant_name" label="申请人" width="90" />
      <el-table-column label="申请途径" width="90">
        <template #default="{ row }">{{ channelLabel(row.create_channel) }}</template>
      </el-table-column>
      <el-table-column label="确认途径" width="90">
        <template #default="{ row }">{{ channelLabel(row.confirm_channel) }}</template>
      </el-table-column>
      <el-table-column prop="created_at" label="申请时间" width="160" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
      <el-table-column prop="reject_reason" label="驳回原因" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'pending' && canConfirm">
            <el-button link type="primary" @click="approve(row)">通过</el-button>
            <el-button link type="danger" @click="reject(row)">驳回</el-button>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <AssetDevicePicker v-model="pickerVisible" @confirm="onDevicePicked" />
  </FormSection>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import FormSection from '@/components/form/FormSection.vue'
import AssetDevicePicker from '@/components/form/AssetDevicePicker.vue'

const props = withDefaults(
  defineProps<{
    module: 'maintain' | 'inspect' | 'pm'
    planId?: string | null
    canConfirm?: boolean
    canInitiate?: boolean
  }>(),
  { canConfirm: true, canInitiate: true }
)

const emit = defineEmits<{ reloaded: [] }>()

const rows = ref<Record<string, unknown>[]>([])
const pickerVisible = ref(false)

const base = () => `/${props.module}/plan`

async function load() {
  if (!props.planId) {
    rows.value = []
    return
  }
  const { data } = await http.get(`${base()}/${props.planId}/include-requests`)
  rows.value = data.data ?? []
}

function statusLabel(st: unknown) {
  if (st === 'pending') return '待确认'
  if (st === 'approved') return '已通过'
  if (st === 'rejected') return '已驳回'
  return String(st ?? '—')
}

const CHANNEL_LABELS: Record<string, string> = { web: 'Web', app: 'App', mp: '小程序' }
function channelLabel(v: unknown) {
  const s = v != null ? String(v).trim() : ''
  if (!s) return '—'
  return CHANNEL_LABELS[s] || s
}

function openApply() {
  if (!props.planId) {
    ElMessage.warning('请先打开计划')
    return
  }
  pickerVisible.value = true
}

async function onDevicePicked(device: Record<string, unknown>) {
  if (!props.planId || !device.id) return
  let remark = ''
  try {
    const { value } = await ElMessageBox.prompt('可选填写申请备注', '发起纳入申请', {
      inputPlaceholder: '备注',
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputValue: ''
    })
    remark = value ?? ''
  } catch {
    return
  }
  await http.post(`${base()}/include-request`, {
    client: 'web',
    plan_id: props.planId,
    device_id: device.id,
    device_code: device.device_code,
    device_name: device.device_name,
    dept_id: device.dept_id,
    remark
  })
  ElMessage.success('已提交纳入申请')
  await load()
}

async function approve(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确认将该设备纳入本计划明细？', '确认纳入', { type: 'warning' })
  } catch {
    return
  }
  await http.post(`${base()}/include-request/${row.id}/approve`, { client: 'web' })
  ElMessage.success('已纳入计划')
  await load()
  emit('reloaded')
}

async function reject(row: Record<string, unknown>) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回纳入', {
      inputPattern: /\S+/,
      inputErrorMessage: '原因不能为空'
    })
    reason = value
  } catch {
    return
  }
  await http.post(`${base()}/include-request/${row.id}/reject`, {
    client: 'web',
    reject_reason: reason
  })
  ElMessage.success('已驳回')
  await load()
}

watch(
  () => props.planId,
  () => {
    load()
  }
)

onMounted(load)

defineExpose({ load, openApply })
</script>

<style scoped>
.include-section {
  margin-top: 12px;
}
.include-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.muted {
  color: var(--el-text-color-placeholder);
}
</style>
