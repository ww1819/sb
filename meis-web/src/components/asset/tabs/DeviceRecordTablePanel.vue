<template>
  <div class="device-record-panel">
    <div v-if="showFilter" class="device-record-panel__toolbar">
      <el-input
        v-model="keyword"
        :placeholder="filterPlaceholder"
        clearable
        class="device-record-panel__input"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe class="system-table device-record-panel__table">
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :min-width="col.minWidth ?? 120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <StatusTag
            v-if="col.dictType"
            :value="row[col.prop]"
            :prop="col.prop"
            :dict-type="col.dictType"
          />
          <template v-else>{{ formatCell(col.prop, row[col.prop]) }}</template>
        </template>
      </el-table-column>
      <template #empty>
        <PageEmpty :description="emptyText" :image-size="72" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import PageEmpty from '@/components/table/PageEmpty.vue'
import StatusTag from '@/components/table/StatusTag.vue'
import { useDict } from '@/composables/useDict'
import { formatDisplayDate, formatDisplayDateTime } from '@/utils/datetime'

export interface RecordColumn {
  prop: string
  label: string
  minWidth?: number
  /** AST-UI-22：字典类型，有则中文展示 */
  dictType?: string
}

const props = withDefaults(
  defineProps<{
    columns: RecordColumn[]
    emptyText?: string
    showFilter?: boolean
    filterPlaceholder?: string
    loadUrl?: string
    deviceId?: string
    /** 额外查询参数，如 include_draft */
    extraParams?: Record<string, unknown>
  }>(),
  {
    emptyText: '暂无数据',
    showFilter: true,
    filterPlaceholder: '关键词搜索'
  }
)

const keyword = ref('')
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const { preloadDictTypes } = useDict()

async function load() {
  if (!props.loadUrl || !props.deviceId) return
  loading.value = true
  try {
    await preloadDictTypes(props.columns.map((c) => c.dictType))
    const url = props.loadUrl.split('{deviceId}').join(props.deviceId)
    const { data } = await http.get(url, {
      params: {
        page: 1,
        size: 50,
        deviceId: props.deviceId,
        keyword: keyword.value || undefined,
        ...(props.extraParams ?? {})
      }
    })
    const payload = data.data
    let list: Record<string, unknown>[] = Array.isArray(payload)
      ? payload
      : ((payload?.records as Record<string, unknown>[]) ?? [])
    const kw = keyword.value.trim().toLowerCase()
    if (kw && Array.isArray(payload)) {
      list = list.filter((row) =>
        Object.values(row).some((v) => String(v ?? '').toLowerCase().includes(kw))
      )
    }
    rows.value = list
  } finally {
    loading.value = false
  }
}

function reset() {
  keyword.value = ''
  load()
}

function formatCell(prop: string, value: unknown) {
  if (value === null || value === undefined || value === '') return '—'
  const p = prop.toLowerCase()
  if (
    p.endsWith('_at') ||
    p.endsWith('_time') ||
    p === 'replaced_at' ||
    p === 'report_time' ||
    p === 'printed_at' ||
    p === 'effective_from' ||
    p === 'effective_to' ||
    p === 'occurred_at'
  ) {
    return formatDisplayDateTime(value)
  }
  if (p.endsWith('_date') || p === 'planned_date' || p === 'next_due_date') {
    return formatDisplayDate(value)
  }
  return String(value)
}

onMounted(load)
watch(() => props.deviceId, load)
watch(() => props.extraParams, load, { deep: true })

defineExpose({ load })
</script>

<style scoped>
.device-record-panel__toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.device-record-panel__input {
  width: 260px;
  max-width: 100%;
}
.device-record-panel__table {
  width: 100%;
}
</style>
