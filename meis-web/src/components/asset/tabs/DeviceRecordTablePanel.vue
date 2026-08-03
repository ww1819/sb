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
          <el-button
            v-if="isLinkCol(col) && row[col.prop]"
            link
            type="primary"
            @click="openDrill(row)"
          >
            {{ formatCell(col.prop, row[col.prop]) }}
          </el-button>
          <StatusTag
            v-else-if="col.dictType"
            :value="row[col.prop]"
            :prop="col.prop"
            :dict-type="col.dictType"
          />
          <template v-else>{{ formatCell(col.prop, row[col.prop]) }}</template>
        </template>
      </el-table-column>
      <el-table-column v-if="drill" label="操作" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDrill(row)">明细</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <PageEmpty :description="emptyText" :image-size="72" />
      </template>
    </el-table>

    <AppModal v-model="drillVisible" :title="drillTitle" size="xl">
      <div v-loading="drillLoading" class="sheet-drill">
        <template v-if="drillDetail">
          <el-descriptions :column="2" border size="small" class="sheet-drill__head">
            <el-descriptions-item
              v-for="f in drillHeadFields"
              :key="f.prop"
              :label="f.label"
            >
              <StatusTag
                v-if="f.dictType"
                :value="drillDetail[f.prop]"
                :prop="f.prop"
                :dict-type="f.dictType"
              />
              <template v-else>{{ formatCell(f.prop, drillDetail[f.prop]) }}</template>
            </el-descriptions-item>
          </el-descriptions>

          <template v-if="drillItems.length">
            <div class="sheet-drill__section">设备明细</div>
            <el-table :data="drillItems" border size="small" max-height="220">
              <el-table-column prop="device_code" label="设备编码" min-width="110" />
              <el-table-column prop="device_name" label="设备名称" min-width="140" />
              <el-table-column prop="status" label="状态" min-width="90">
                <template #default="{ row }">
                  <StatusTag
                    :value="row.status"
                    prop="status"
                    :dict-type="drill?.itemStatusDict"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="overall_result" label="结果" min-width="90">
                <template #default="{ row }">
                  <StatusTag
                    v-if="row.overall_result != null && row.overall_result !== ''"
                    :value="row.overall_result"
                    prop="overall_result"
                    :dict-type="drill?.resultDict ?? 'maintain_result'"
                  />
                  <template v-else>—</template>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
          </template>

          <template v-if="drillResults.length">
            <div class="sheet-drill__section">执行结果明细</div>
            <el-table :data="drillResults" border size="small" max-height="280">
              <el-table-column prop="item_name" label="项目" min-width="140" />
              <el-table-column prop="item_content" label="内容" min-width="160" show-overflow-tooltip />
              <el-table-column prop="standard_value" label="标准值" min-width="100" />
              <el-table-column prop="result_value" label="结果值" min-width="110" />
              <el-table-column prop="result_status" label="结果状态" min-width="100">
                <template #default="{ row }">
                  <StatusTag
                    :value="row.result_status"
                    prop="result_status"
                    dict-type="ops_result_status"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
            </el-table>
          </template>

          <PageEmpty
            v-if="!drillItems.length && !drillResults.length && drill?.kind !== 'generic'"
            description="暂无明细或执行结果"
            :image-size="64"
          />
        </template>
      </div>
      <template #footer>
        <el-button type="primary" @click="drillVisible = false">关闭</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
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
  /** 作为下钻入口（通常业务单号列） */
  link?: boolean
}

/** PLT-DEV-SHEET-01 / AST-SHEET-DRILL-01 */
export interface SheetDrillConfig {
  /** 详情 API，占位符 {id} */
  detailUrl: string
  /** 从列表行取详情 id，默认 id */
  idProp?: string
  /** 抽屉标题用字段，默认优先 execution_no / plan_no / wo_no / check_no / biz_no */
  titleProp?: string
  titlePrefix?: string
  /** execution=头+items+results；item=当前行详情含 results；plan/inventory/repair/generic */
  kind?: 'execution' | 'item' | 'plan' | 'inventory' | 'repair' | 'generic'
  itemStatusDict?: string
  resultDict?: string
  /** 头信息展示字段 */
  headFields?: { prop: string; label: string; dictType?: string }[]
}

const props = withDefaults(
  defineProps<{
    columns: RecordColumn[]
    emptyText?: string
    showFilter?: boolean
    filterPlaceholder?: string
    loadUrl?: string
    deviceId?: string
    extraParams?: Record<string, unknown>
    /** 下钻配置；未配置则仅列表 */
    drill?: SheetDrillConfig
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
const drillVisible = ref(false)
const drillLoading = ref(false)
const drillDetail = ref<Record<string, unknown> | null>(null)
const drillSourceRow = ref<Record<string, unknown> | null>(null)
const { preloadDictTypes } = useDict()

const drillTitle = computed(() => {
  const prefix = props.drill?.titlePrefix ?? '单据明细'
  const tp = props.drill?.titleProp
  const row = drillSourceRow.value
  const detail = drillDetail.value
  const no =
    (tp && row?.[tp]) ||
    detail?.execution_no ||
    detail?.plan_no ||
    detail?.plan_code ||
    detail?.wo_no ||
    detail?.check_no ||
    detail?.biz_no ||
    row?.execution_no ||
    row?.plan_no ||
    ''
  return no ? `${prefix} ${no}` : prefix
})

const drillHeadFields = computed(() => {
  if (props.drill?.headFields?.length) return props.drill.headFields
  const kind = props.drill?.kind ?? 'generic'
  if (kind === 'execution' || kind === 'item') {
    return [
      { prop: 'execution_no', label: '执行单号' },
      { prop: 'plan_no', label: '计划单号' },
      {
        prop: 'status',
        label: '状态',
        dictType: props.drill?.itemStatusDict
      },
      {
        prop: 'execution_status',
        label: '执行状态',
        dictType:
          props.drill?.itemStatusDict?.replace('_item_status', '_status') ??
          props.drill?.itemStatusDict
      },
      { prop: 'planned_date', label: '计划日期' },
      { prop: 'overall_result', label: '结果', dictType: props.drill?.resultDict },
      { prop: 'template_name', label: '模板' },
      { prop: 'org_name', label: '机构' },
      { prop: 'certificate_no', label: '证书号' },
      { prop: 'remark', label: '备注' }
    ]
  }
  if (kind === 'plan') {
    return [
      { prop: 'plan_no', label: '计划单号' },
      { prop: 'plan_code', label: '计划编号' },
      { prop: 'plan_name', label: '计划名称' },
      { prop: 'approval_status', label: '审核状态', dictType: 'approval_status' },
      { prop: 'plan_status', label: '计划状态', dictType: 'plan_status' },
      { prop: 'status', label: '状态', dictType: 'plan_status' },
      { prop: 'next_due_date', label: '下次到期' },
      { prop: 'remark', label: '备注' }
    ]
  }
  if (kind === 'inventory') {
    return [
      { prop: 'check_no', label: '盘点单号' },
      { prop: 'check_name', label: '盘点名称' },
      { prop: 'check_type', label: '类型', dictType: 'check_type' },
      { prop: 'status', label: '状态', dictType: 'check_status' },
      { prop: 'audit_status', label: '审核', dictType: 'audit_status' },
      { prop: 'remark', label: '备注' }
    ]
  }
  if (kind === 'repair') {
    return [
      { prop: 'wo_no', label: '工单号' },
      { prop: 'status', label: '状态', dictType: 'wo_status' },
      { prop: 'fault_description', label: '故障描述' },
      { prop: 'assigned_user_name', label: '工程师' },
      { prop: 'report_time', label: '报修时间' }
    ]
  }
  return [
    { prop: 'id', label: 'ID' },
    { prop: 'remark', label: '备注' }
  ]
})

const drillItems = computed(() => {
  const d = drillDetail.value
  if (!d) return []
  if (Array.isArray(d.items)) return d.items as Record<string, unknown>[]
  return []
})

const drillResults = computed(() => {
  const d = drillDetail.value
  if (!d) return []
  if (Array.isArray(d.results)) return d.results as Record<string, unknown>[]
  const items = drillItems.value
  if (!items.length) return []
  const focusId = drillSourceRow.value?.id
  const focused = focusId
    ? items.find((i) => String(i.id) === String(focusId))
    : undefined
  const pool = focused ? [focused] : items
  const out: Record<string, unknown>[] = []
  for (const item of pool) {
    const rs = item.results
    if (Array.isArray(rs)) out.push(...(rs as Record<string, unknown>[]))
  }
  return out
})

function isLinkCol(col: RecordColumn) {
  if (!props.drill) return false
  if (col.link) return true
  const tp = props.drill.titleProp
  return !!tp && col.prop === tp
}

async function load() {
  if (!props.loadUrl || !props.deviceId) return
  loading.value = true
  try {
    await preloadDictTypes([
      ...props.columns.map((c) => c.dictType),
      props.drill?.itemStatusDict,
      props.drill?.resultDict,
      'ops_result_status',
      'approval_status',
      'plan_status',
      'check_type',
      'check_status',
      'audit_status',
      'wo_status'
    ])
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
    p === 'occurred_at' ||
    p === 'bound_at' ||
    p === 'unbound_at' ||
    p === 'completed_at'
  ) {
    return formatDisplayDateTime(value)
  }
  if (p.endsWith('_date') || p === 'planned_date' || p === 'next_due_date') {
    return formatDisplayDate(value)
  }
  return String(value)
}

function resolveDetailUrl(row: Record<string, unknown>) {
  const cfg = props.drill
  if (!cfg?.detailUrl) return ''
  const idProp = cfg.idProp ?? 'id'
  const id = row[idProp]
  if (id == null || id === '') return ''
  return cfg.detailUrl.split('{id}').join(String(id))
}

async function openDrill(row: Record<string, unknown>) {
  if (!props.drill) {
    ElMessage.info('本 Sheet 未配置下钻')
    return
  }
  const url = resolveDetailUrl(row)
  if (!url) {
    ElMessage.warning('缺少单据标识，无法打开明细')
    return
  }
  drillSourceRow.value = row
  drillVisible.value = true
  drillLoading.value = true
  drillDetail.value = null
  try {
    const { data } = await http.get(url)
    drillDetail.value = (data.data as Record<string, unknown>) ?? null
    if (!drillDetail.value || !Object.keys(drillDetail.value).length) {
      ElMessage.warning('未找到单据详情')
    }
  } finally {
    drillLoading.value = false
  }
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
.sheet-drill__head {
  margin-bottom: 12px;
}
.sheet-drill__section {
  margin: 12px 0 8px;
  font-weight: 600;
  font-size: 13px;
}
</style>
