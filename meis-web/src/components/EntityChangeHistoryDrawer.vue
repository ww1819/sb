<template>
  <AppModal v-model="visible" title="变更记录" size="lg" @close="onClose">
    <el-table v-loading="loading" :data="rows" stripe max-height="480" empty-text="暂无变更记录">
      <el-table-column prop="created_at" label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.created_at) }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="100">
        <template #default="{ row }">{{ actionLabel(row.action) }}</template>
      </el-table-column>
      <el-table-column prop="operator_name" label="操作人" width="100" />
      <el-table-column label="变更字段" min-width="280">
        <template #default="{ row }">
          <div v-if="fieldList(row).length" class="field-changes">
            <div v-for="(f, i) in fieldList(row)" :key="i" class="field-row">
              <span class="field-name">{{ resolveLabel(f.field, f.label) }}</span>
              <span class="old">{{ displayVal(f.oldValue) }}</span>
              <span class="arrow">→</span>
              <span class="new">{{ displayVal(f.newValue) }}</span>
            </div>
          </div>
          <span v-else-if="row.remark" class="muted">{{ row.remark }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        small
        @current-change="load"
      />
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import { getSchema } from '@/config/pageSchemas'

const props = defineProps<{
  modelValue: boolean
  entityType: string
  entityId?: string | null
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const labelMap = computed(() => {
  const map = new Map<string, string>()
  for (const f of getSchema(props.entityType)) {
    map.set(f.prop, f.label)
  }
  return map
})

watch(
  () => [props.modelValue, props.entityType, props.entityId] as const,
  ([open, type, id]) => {
    if (open && type && id) {
      page.value = 1
      void load()
    }
  }
)

async function load() {
  if (!props.entityType || !props.entityId) return
  loading.value = true
  try {
    const { data } = await http.get('/system/entity-change-log', {
      params: {
        entityType: props.entityType,
        entityId: props.entityId,
        page: page.value,
        size: size.value
      }
    })
    rows.value = data.data?.records ?? data.data?.list ?? []
    total.value = Number(data.data?.total ?? 0)
  } finally {
    loading.value = false
  }
}

function onClose() {
  emit('update:modelValue', false)
}

function fmt(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

function actionLabel(a: unknown) {
  const m: Record<string, string> = {
    create: '创建',
    update: '修改',
    delete: '删除',
    submit: '提交',
    withdraw: '撤回',
    cancel: '取消'
  }
  return m[String(a)] ?? String(a ?? '')
}

function fieldList(row: Record<string, unknown>) {
  const raw = row.changed_fields
  if (Array.isArray(raw)) return raw as { field: string; label?: string; oldValue?: unknown; newValue?: unknown }[]
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function resolveLabel(field: string, fallback?: string) {
  return labelMap.value.get(field) ?? fallback ?? field
}

function displayVal(v: unknown) {
  if (v === null || v === undefined || v === '') return '（空）'
  return String(v)
}
</script>

<style scoped>
.field-changes {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}
.field-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}
.field-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 72px;
}
.old {
  color: var(--el-color-danger);
  text-decoration: line-through;
}
.arrow {
  color: var(--el-text-color-secondary);
}
.new {
  color: var(--el-color-success);
}
.muted {
  color: var(--el-text-color-secondary);
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
