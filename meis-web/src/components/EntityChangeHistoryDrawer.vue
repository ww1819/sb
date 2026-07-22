<template>
  <AppModal v-model="visible" title="变更记录" size="lg" @close="onClose">
    <el-table v-loading="loading" :data="displayRows" stripe max-height="480" empty-text="暂无变更记录">
      <el-table-column prop="created_at" label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.created_at) }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="88">
        <template #default="{ row }">{{ actionLabel(row.action) }}</template>
      </el-table-column>
      <el-table-column prop="operator_name" label="操作人" width="100" show-overflow-tooltip />
      <el-table-column prop="fieldLabel" label="变更字段" min-width="120" show-overflow-tooltip />
      <el-table-column prop="oldText" label="改动前" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ muted: row.oldText === '（空）', old: row.oldText !== '（空）' && row.hasField }">
            {{ row.oldText }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="newText" label="改动后" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ muted: row.newText === '（空）', new: row.newText !== '（空）' && row.hasField }">
            {{ row.newText }}
          </span>
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
import { getSchema, type FieldSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'
import { preloadRefLabelMaps, resolveRefLabel, labelCacheVersion } from '@/composables/useRefLabelMap'
import { formatStatusLabel } from '@/utils/tableCell'

type FieldChange = {
  field: string
  label?: string
  oldValue?: unknown
  newValue?: unknown
  old_value?: unknown
  new_value?: unknown
}

type DisplayRow = {
  created_at?: unknown
  action?: unknown
  operator_name?: unknown
  remark?: unknown
  fieldLabel: string
  oldText: string
  newText: string
  hasField: boolean
}

/** 对用户无意义的技术字段，不在变更记录中展示 */
const HIDDEN_FIELDS = new Set([
  'extension_data',
  'manual_files',
  'certificate_files',
  'qr_code_url',
  'row_version',
  'permissions',
  'snapshot_json'
])

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
const refsReady = ref(0)

const { preloadDictTypes, resolveDictLabel, cacheVersion } = useDict()

const schemaByProp = computed(() => {
  const map = new Map<string, FieldSchema>()
  for (const f of getSchema(props.entityType)) {
    map.set(f.prop, f)
  }
  return map
})

const displayRows = computed(() => {
  void cacheVersion.value
  void labelCacheVersion.value
  void refsReady.value
  const out: DisplayRow[] = []
  for (const row of rows.value) {
    const fields = fieldList(row).filter((f) => !HIDDEN_FIELDS.has(f.field))
    if (fields.length) {
      for (const f of fields) {
        out.push({
          created_at: row.created_at,
          action: row.action,
          operator_name: row.operator_name,
          remark: row.remark,
          fieldLabel: resolveLabel(f.field, f.label),
          oldText: formatPlainValue(f.field, f.oldValue ?? f.old_value),
          newText: formatPlainValue(f.field, f.newValue ?? f.new_value),
          hasField: true
        })
      }
    } else if (!fieldList(row).length) {
      out.push({
        created_at: row.created_at,
        action: row.action,
        operator_name: row.operator_name,
        remark: row.remark,
        fieldLabel: '—',
        oldText: '—',
        newText: '—',
        hasField: false
      })
    }
  }
  return out
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
    await preloadDisplayMeta(rows.value)
  } finally {
    loading.value = false
  }
}

async function preloadDisplayMeta(logRows: Record<string, unknown>[]) {
  const dictTypes = new Set<string>()
  const linkTables = new Set<string>()
  for (const row of logRows) {
    for (const f of fieldList(row)) {
      if (HIDDEN_FIELDS.has(f.field)) continue
      const schema = schemaByProp.value.get(f.field)
      if (schema?.dictType) dictTypes.add(schema.dictType)
      if (schema?.linkTable) linkTables.add(schema.linkTable)
    }
  }
  await Promise.all([
    preloadDictTypes([...dictTypes]),
    preloadRefLabelMaps([...linkTables])
  ])
  refsReady.value += 1
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

function fieldList(row: Record<string, unknown>): FieldChange[] {
  const raw = row.changed_fields ?? row.changedFields
  const parsed = coerceJsonArray(raw)
  return parsed.filter((x) => x && typeof x === 'object' && (x as FieldChange).field) as FieldChange[]
}

function coerceJsonArray(raw: unknown): unknown[] {
  if (raw == null || raw === '') return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    if (typeof obj.value === 'string') return coerceJsonArray(obj.value)
    if (Array.isArray(obj.fields)) return obj.fields
  }
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
  if (fallback && fallback !== field) return fallback
  return schemaByProp.value.get(field)?.label ?? fallback ?? field
}

function formatPlainValue(field: string, value: unknown): string {
  if (value === null || value === undefined || value === '') return '（空）'

  const schema = schemaByProp.value.get(field)
  const raw = unwrapTechnical(value)

  // 布尔 / is_*
  if (schema?.type === 'boolean' || field.startsWith('is_') || typeof raw === 'boolean') {
    return formatStatusLabel(raw, field.startsWith('is_') ? field : 'is_flag')
  }
  if (raw === 'true' || raw === 'false') {
    return raw === 'true' ? '是' : '否'
  }

  // 字典
  if (schema?.dictType) {
    const label = resolveDictLabel(schema.dictType, raw)
    if (label) return label
  }

  // 外键
  if (schema?.linkTable) {
    const name = resolveRefLabel(schema.linkTable, raw)
    if (name && name !== String(raw)) return name
  }

  // 技术 JSON / PGobject 噪音
  if (isTechnicalBlob(raw)) return '（内部数据）'

  return String(raw)
}

function unwrapTechnical(value: unknown): unknown {
  if (value == null) return value
  if (typeof value !== 'string') return value
  const s = value.trim()
  if ((s.startsWith('{') || s.startsWith('[')) && (s.includes('"type":"jsonb"') || s.includes('"type": "jsonb"'))) {
    try {
      const obj = JSON.parse(s) as Record<string, unknown>
      if (typeof obj.value === 'string') return obj.value
      if ('value' in obj) return obj.value
    } catch {
      /* keep */
    }
  }
  return value
}

function isTechnicalBlob(value: unknown): boolean {
  const s = String(value ?? '')
  if (s.includes('"type":"jsonb"') || s.includes('"type": "jsonb"')) return true
  if (s.length > 120 && (s.startsWith('{') || s.startsWith('['))) return true
  return false
}
</script>

<style scoped>
.old {
  color: var(--el-color-danger);
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
