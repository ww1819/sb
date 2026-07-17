<template>
  <el-drawer
    v-model="visible"
    :title="title"
    direction="rtl"
    size="420px"
    append-to-body
    destroy-on-close
  >
    <div v-loading="loading" class="progress-body">
      <el-alert
        v-if="instanceStatusLabel"
        :title="`审批状态：${instanceStatusLabel}`"
        :type="instanceAlertType"
        show-icon
        :closable="false"
        class="status-alert"
      />
      <el-empty v-if="!loading && !nodes.length" description="暂无审批流程节点，请先配置审批流" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="n in nodes"
          :key="String(n.id ?? n.node_order)"
          :type="timelineType(n.node_status)"
          :hollow="n.node_status === 'waiting' || n.node_status === 'pending_submit'"
          :timestamp="formatTime(n.acted_at)"
          placement="top"
        >
          <div class="node-card">
            <div class="node-title">
              <span class="name">{{ n.node_name || `节点 ${n.node_order}` }}</span>
              <el-tag size="small" :type="tagType(n.node_status)">{{ n.node_status_label }}</el-tag>
            </div>
            <div v-if="n.approver_role" class="meta">审批角色：{{ n.approver_role }}</div>
            <div v-if="n.approver_name" class="meta">处理人：{{ n.approver_name }}</div>
            <div v-if="n.action" class="meta">动作：{{ actionLabel(n.action) }}</div>
            <div v-if="n.comment" class="comment">{{ n.comment }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import http from '@/api/http'

const props = defineProps<{
  modelValue: boolean
  businessType: string
  businessId?: string | null
  title?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const nodes = ref<Record<string, unknown>[]>([])
const instance = ref<Record<string, unknown> | null>(null)

const title = computed(() => props.title || '审批进度')

const instanceStatusLabel = computed(() => {
  const s = instance.value?.status
  if (s == null || s === '') return nodes.value.length ? '未提交审批' : ''
  const map: Record<string, string> = {
    pending: '审批中',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
    draft: '草稿'
  }
  return map[String(s)] ?? String(s)
})

const instanceAlertType = computed(() => {
  const s = String(instance.value?.status ?? '')
  if (s === 'approved') return 'success'
  if (s === 'rejected') return 'error'
  if (s === 'pending') return 'warning'
  return 'info'
})

function timelineType(status: unknown): '' | 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (String(status)) {
    case 'approved':
      return 'success'
    case 'current':
      return 'primary'
    case 'rejected':
      return 'danger'
    case 'waiting':
    case 'pending_submit':
      return 'info'
    default:
      return 'info'
  }
}

function tagType(status: unknown): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (String(status)) {
    case 'approved':
      return 'success'
    case 'current':
      return 'warning'
    case 'rejected':
      return 'danger'
    default:
      return 'info'
  }
}

function actionLabel(action: unknown) {
  const a = String(action ?? '')
  if (a === 'approve') return '通过'
  if (a === 'reject') return '驳回'
  return a
}

function formatTime(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  if (!props.businessId || !props.businessType) {
    nodes.value = []
    instance.value = null
    return
  }
  loading.value = true
  try {
    const { data } = await http.get('/system/approval/business/progress', {
      params: { businessType: props.businessType, businessId: props.businessId }
    })
    const payload = (data.data ?? {}) as Record<string, unknown>
    instance.value = (payload.instance as Record<string, unknown>) ?? null
    nodes.value = (payload.nodes as Record<string, unknown>[]) ?? []
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.businessId, props.businessType] as const,
  ([open]) => {
    if (open) void load()
  }
)
</script>

<style scoped>
.progress-body {
  min-height: 160px;
  padding: 0 4px 12px;
}
.status-alert {
  margin-bottom: 16px;
}
.node-card {
  padding-right: 8px;
}
.node-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.node-title .name {
  font-weight: 600;
  color: var(--meis-text-primary, #303133);
}
.meta {
  font-size: 13px;
  color: var(--meis-text-secondary, #606266);
  line-height: 1.6;
}
.comment {
  margin-top: 4px;
  font-size: 13px;
  color: var(--meis-text-regular, #606266);
}
</style>
