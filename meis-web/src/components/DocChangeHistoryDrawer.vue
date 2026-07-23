<template>
  <AppModal v-model="visible" title="修改记录" size="lg">
    <el-table v-loading="loading" :data="rows" stripe max-height="480" empty-text="暂无修改记录">
      <el-table-column prop="created_at" label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.created_at) }}</template>
      </el-table-column>
      <el-table-column prop="event_type" label="事件" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ eventLabel(row.event_type) }}</template>
      </el-table-column>
      <el-table-column prop="entity_type" label="对象" width="80" show-overflow-tooltip />
      <el-table-column prop="field_name" label="字段" width="120" show-overflow-tooltip />
      <el-table-column prop="old_value" label="改前" min-width="120" show-overflow-tooltip />
      <el-table-column prop="new_value" label="改后" min-width="120" show-overflow-tooltip />
      <el-table-column prop="operator_name" label="操作人" width="100" show-overflow-tooltip />
      <el-table-column prop="client" label="端" width="70" />
      <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
    </el-table>
    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'

const props = defineProps<{
  modelValue: boolean
  apiUrl?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const visible = ref(false)
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])

const EVENT_LABELS: Record<string, string> = {
  create: '创建',
  update: '更新',
  approve: '审核通过',
  rejected: '审核驳回',
  field_change: '字段变更',
  start: '开始',
  submit: '提交',
  withdraw: '撤回',
  audit_approve: '审核通过',
  audit_reject: '审核驳回',
  complete_item: '完成明细'
}

watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
    if (v) load()
  }
)
watch(visible, (v) => emit('update:modelValue', v))

async function load() {
  if (!props.apiUrl) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const { data } = await http.get(props.apiUrl)
    rows.value = (data.data as Record<string, unknown>[]) ?? []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function fmt(v: unknown) {
  if (v == null) return ''
  const s = String(v)
  return s.length >= 19 ? s.slice(0, 19).replace('T', ' ') : s
}

function eventLabel(v: unknown) {
  const k = String(v ?? '')
  return EVENT_LABELS[k] ?? k
}
</script>
