<template>
  <SystemPageCard
    :title="config.title"
    :loading="loading"
    show-pager
    v-model:page="page"
    v-model:size="size"
    :total="total"
    @page-change="load"
  >
    <template #filterBar>
      <PageFilterBar
        v-model:keyword="keyword"
        placeholder="关键词搜索"
        :show-search-buttons="useActionsRowToolbar"
        @search="onSearch"
        @reset="onReset"
      >
        <template v-if="moreSearchFields.length" #keyword>
          <MoreSearchPanel
            :fields="moreSearchFields"
            v-model="moreSearchValues"
            v-model:labels="moreSearchLabels"
            v-model:selected="selectedMoreSearchKeys"
            @search="onSearch"
          />
        </template>
        <template v-if="prependFilters.length" #prepend>
          <CrudListFilterField
            v-for="f in prependFilters"
            :key="f.key"
            :filter="f"
            v-model="filterValues[f.key]"
            :options="filterOptions[f.key] ?? []"
            start-placeholder="起"
            end-placeholder="止"
            @change="onSearch"
          />
        </template>
        <template v-if="normalFilters.length" #filters>
          <CrudListFilterField
            v-for="f in normalFilters"
            :key="f.key"
            :filter="f"
            v-model="filterValues[f.key]"
            :options="filterOptions[f.key] ?? []"
            @change="onSearch"
          />
        </template>
        <template v-if="!useActionsRowToolbar" #trailing>
          <el-button v-if="showImport" @click="importVisible = true">导入</el-button>
          <el-button v-if="showPinyinCode" @click="openPinyinDialog">生成简码</el-button>
          <slot name="toolbar-extra" />
        </template>
        <template #actions>
          <template v-if="useActionsRowToolbar">
            <CrudListFilterField
              v-for="f in actionBarFilters"
              :key="f.key"
              :filter="f"
              v-model="filterValues[f.key]"
              :options="filterOptions[f.key] ?? []"
              @change="onSearch"
            />
            <el-button v-if="!hideAdd" v-permission="'add'" type="primary" @click="onAdd">新增</el-button>
            <el-button @click="exportCsv">导出</el-button>
            <el-button v-if="showImport" @click="importVisible = true">导入</el-button>
            <el-button v-if="showPinyinCode" @click="openPinyinDialog">生成简码</el-button>
            <slot name="toolbar-extra" />
            <slot name="actions-after" />
          </template>
          <template v-else>
            <CrudListFilterField
              v-for="f in actionBarFilters"
              :key="f.key"
              :filter="f"
              v-model="filterValues[f.key]"
              :options="filterOptions[f.key] ?? []"
              @change="onSearch"
            />
            <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
            <el-button v-if="!hideAdd" v-permission="'add'" type="primary" @click="onAdd">新增</el-button>
            <el-button @click="exportCsv">导出</el-button>
            <slot name="actions-after" />
          </template>
        </template>
      </PageFilterBar>
    </template>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      row-key="id"
      stripe
      class="system-table"
      :height="tableHeight"
      @row-dblclick="onRowDblClick"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="hasSelectionColumn" type="selection" width="48" fixed="left" reserve-selection />
      <el-table-column
        v-if="showRowIndex"
        label="序号"
        width="64"
        fixed="left"
        align="center"
        class-name="col-row-index"
      >
        <template #default="{ $index }">{{ rowSerial($index) }}</template>
      </el-table-column>
      <el-table-column
        v-for="f in listFields"
        :key="f.prop"
        :prop="f.prop"
        :label="f.label"
        :align="columnAlign(f.prop, f.type)"
        :min-width="f.width ?? 120"
        show-overflow-tooltip
      >
        <template v-if="isSortable(f.prop)" #header>
          <TableColumnSortHeader
            :label="f.label"
            :field="f.prop"
            :sort-field="sortField"
            :sort-order="sortOrder"
            @sort="(field, order) => setSort(field, order)"
          />
        </template>
        <template #default="{ row }">
          <TableCellValue :field="f" :value="row[f.prop]" />
        </template>
      </el-table-column>
      <slot name="extra-columns" />
      <el-table-column
        v-if="!hideOperationColumn"
        label="操作"
        header-align="center"
        align="center"
        :width="operationWidth"
        fixed="right"
        class-name="col-operations"
      >
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              v-if="viewEnabled && canViewRow(row)"
              link
              type="primary"
              @click="onView(row)"
            >
              查看
            </el-button>
            <el-button
              v-if="canEditRow(row)"
              link
              type="primary"
              @click="onEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-else-if="detailMode"
              link
              type="primary"
              @click="onEdit(row)"
            >
              查看
            </el-button>
            <el-button v-if="canDeleteRow(row)" link type="danger" @click="remove(row)">删除</el-button>
            <slot name="row-actions" :row="row" />
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <PageEmpty description="暂无数据" />
      </template>
    </el-table>

    <FormDrawer
      v-if="!detailMode"
      v-model="formVisible"
      :title="formTitle"
      size="lg"
      :placement="formPlacement"
      :show-save="formMode !== 'view'"
      @save="save"
    >
      <div v-if="formMode === 'view' && form.id && changeLogEnabled" class="change-log-bar">
        <el-button @click="openChangeLog">变更记录</el-button>
      </div>
      <slot name="form" :form="form" :fields="formFields" :mode="formMode">
        <el-form label-width="120px" :disabled="formMode === 'view'">
          <GroupedFormFields
            :table="config.table"
            :model="form"
            :fields="formFields"
            :group-columns="config.formGroupColumns"
          />
        </el-form>
      </slot>
    </FormDrawer>

    <EntityChangeHistoryDrawer
      v-model="changeLogVisible"
      :entity-type="config.table"
      :entity-id="changeLogEntityId"
    />

    <ImportDialog
      v-if="showImport"
      v-model="importVisible"
      :title="`${config.title}导入`"
      :import-url="importUrl"
      :template-url="importTemplateUrl"
      :template-filename="`${config.table}_import_template.xlsx`"
      @success="onImportSuccess"
    />
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, reactive, ref, useSlots, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshLeft, Search } from '@element-plus/icons-vue'
import http from '@/api/http'
import { downloadApiFile } from '@/utils/fileDownload'
import CrudListFilterField from './CrudListFilterField.vue'
import FormDrawer from './FormDrawer.vue'
import FieldRenderer from './FieldRenderer.vue'
import SystemPageCard from './system/SystemPageCard.vue'
import PageFilterBar from './system/PageFilterBar.vue'
import MoreSearchPanel from './system/MoreSearchPanel.vue'
import TableCellValue from './table/TableCellValue.vue'
import TableColumnSortHeader from './table/TableColumnSortHeader.vue'
import PageEmpty from './table/PageEmpty.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getListFields, getSchema, collectLinkTables } from '@/config/pageSchemas'
import GroupedFormFields from './form/GroupedFormFields.vue'
import ImportDialog from './ImportDialog.vue'
import EntityChangeHistoryDrawer from './EntityChangeHistoryDrawer.vue'
import { columnAlign } from '@/utils/tableCell'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'
import { useDict } from '@/composables/useDict'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope, assertScopeSelection } from '@/composables/useListActionScope'
import { executePinyinGenerate, promptPinyinScope } from '@/composables/usePinyinGenerate'
import { preloadRefLabelMaps } from '@/composables/useRefLabelMap'

const props = defineProps<{
  config: PageConfig
  detailMode?: boolean
  hideAdd?: boolean
  deleteUrl?: string
  /** 启用「查看」操作（编辑与删除之间） */
  enableView?: boolean
  canEdit?: (row: Record<string, unknown>) => boolean
  canDelete?: (row: Record<string, unknown>) => boolean
  canView?: (row: Record<string, unknown>) => boolean
  /** 操作列宽度（含自定义 row-actions 时可加大） */
  operationColumnWidth?: number
  /** 隐藏默认「操作」列（由 extra-columns 自行挂功能列，见附录 U.14） */
  hideOperationColumn?: boolean
  /** 追加到列表查询的参数（如左侧树选中节点） */
  extraQuery?: Record<string, string | number | boolean | undefined | null>
  /** 新增表单默认值 */
  defaultFormValues?: Record<string, unknown>
}>()
const emit = defineEmits<{
  detail: [row: Record<string, unknown>]
  add: []
  deleted: [row: Record<string, unknown>]
  saved: [payload: Record<string, unknown>]
  imported: []
}>()
const slots = useSlots()
const { loadDict, preloadDictTypes } = useDict()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const moreSearchValues = ref<Record<string, string>>({})
const moreSearchLabels = ref<Record<string, string>>({})
const selectedMoreSearchKeys = ref<string[]>([])
const filterValues = reactive<Record<string, string | number | string[] | undefined>>({})
const filterOptions = reactive<Record<string, { label: string; value: string }[]>>({})
const formVisible = ref(false)
const importVisible = ref(false)
const form = ref<Record<string, unknown>>({})
const formTitle = ref('新增')
const formMode = ref<'create' | 'edit' | 'view'>('create')
const tableRef = ref()
const changeLogVisible = ref(false)
const changeLogEntityId = ref<string>('')
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc' | null>(null)
const selectedRows = ref<Record<string, unknown>[]>([])
const {
  selectedCount,
  syncFromTable,
  selectedIds,
  clear: clearSelection,
  clearAll
} = useCrossPageSelection()

const tableHeight = useSystemTableHeight()

watch(tableHeight, () => {
  nextTick(() => tableRef.value?.doLayout?.())
})

const viewEnabled = computed(() => props.enableView === true || props.config.enableView === true)
const hideOperationColumn = computed(() => props.hideOperationColumn === true)
const operationWidth = computed(() => {
  if (props.operationColumnWidth) return props.operationColumnWidth
  const hasRowPrint = !!slots['row-actions']
  if (viewEnabled.value) return hasRowPrint ? 236 : 200
  return hasRowPrint ? 200 : 168
})
const changeLogEnabled = computed(() => props.config.enableChangeLog !== false && viewEnabled.value)
const showRowIndex = computed(() => props.config.showRowIndex === true)
const showRowSelection = computed(() => props.config.showRowSelection === true)
const hasSelectionColumn = computed(() => showRowSelection.value || showPinyinCode.value)
const useActionsRowToolbar = computed(() => props.config.toolbarLayout === 'actions-row')
const formPlacement = computed(() => props.config.formPlacement === 'right' ? 'right' : 'center')

const prependFilters = computed(() => props.config.listFilters?.filter((f) => f.prepend) ?? [])
const actionBarFilters = computed(() => props.config.listFilters?.filter((f) => f.actionBar) ?? [])
const normalFilters = computed(() => props.config.listFilters?.filter((f) => !f.prepend && !f.actionBar) ?? [])
const moreSearchFields = computed(() => props.config.moreSearchFields ?? [])

function initMoreSearchValues() {
  for (const f of moreSearchFields.value) {
    if (!(f.key in moreSearchValues.value)) {
      moreSearchValues.value[f.key] = ''
    }
  }
}
watch(moreSearchFields, initMoreSearchValues, { immediate: true })

function isSortable(prop: string) {
  return props.config.sortableColumns?.includes(prop) ?? false
}

function setSort(prop: string, order: 'asc' | 'desc') {
  if (sortField.value === prop && sortOrder.value === order) {
    sortField.value = null
    sortOrder.value = null
  } else {
    sortField.value = prop
    sortOrder.value = order
  }
  page.value = 1
  void load()
}

function rowSerial(index: number) {
  return (page.value - 1) * size.value + index + 1
}

const schema = computed(() => getSchema(props.config.table))
const listFields = computed(() => {
  const s = getListFields(props.config.table)
  if (s.length) return s
  const first = rows.value[0]
  if (!first) return [{ prop: 'id', label: 'id' }]
  return Object.keys(first).filter((k) => !['password_hash'].includes(k)).slice(0, 12).map((k) => ({ prop: k, label: k }))
})
const formFields = computed(() => {
  if (formMode.value === 'view') {
    const all = schema.value
    if (all.length) return all
    return listFields.value
  }
  const s = schema.value.filter((f) => !f.readonly)
  if (s.length) return s
  return listFields.value
})

const showImport = computed(() => props.config.importable === true)
const showPinyinCode = computed(() => props.config.pinyinCode === true)
const importUrl = computed(() => props.config.importUrl ?? `${props.config.apiBase}/${props.config.table}/import`)
const importTemplateUrl = computed(() => props.config.importTemplateUrl ?? `${props.config.apiBase}/${props.config.table}/import/template`)
const exportUrl = computed(() => props.config.exportUrl ?? `${props.config.apiBase}/${props.config.table}/export`)
const pinyinCodeUrl = computed(() => props.config.pinyinCodeUrl ?? `${props.config.apiBase}/${props.config.table}/generate-pinyin`)

function canEditRow(row: Record<string, unknown>) {
  return props.canEdit ? props.canEdit(row) : true
}

function canDeleteRow(row: Record<string, unknown>) {
  return props.canDelete ? props.canDelete(row) : true
}

function canViewRow(row: Record<string, unknown>) {
  return props.canView ? props.canView(row) : true
}

async function loadRefLabels() {
  const tables = [props.config.table]
  if (props.config.detailTable) tables.push(props.config.detailTable)
  await preloadRefLabelMaps(collectLinkTables(...tables))
}

async function load() {
  loading.value = true
  try {
    const url = props.config.listPageUrl ?? `${props.config.apiBase}/${props.config.table}/page`
    const params: Record<string, string | number> = {
      page: page.value,
      size: size.value
    }
    if (!moreSearchFields.value.length && keyword.value) params.keyword = keyword.value
    for (const f of moreSearchFields.value) {
      const v = moreSearchValues.value[f.key]?.trim()
      if (!v) continue
      params[f.key] = v
      if (f.linkTable && f.key.endsWith('_id')) {
        const nameKey = f.key.replace(/_id$/, '_name')
        const label = moreSearchLabels.value[f.key]?.trim()
        if (label) {
          const name = label.replace(/\s*\([^)]*\)\s*$/, '').trim()
          if (name) params[nameKey] = name
        }
      }
    }
    if (props.config.listMode) params.mode = props.config.listMode
    for (const f of props.config.listFilters ?? []) {
      const v = filterValues[f.key]
      if (f.type === 'daterange') {
        const range = v as string[] | undefined
        if (Array.isArray(range) && range[0]) params[`${f.key}From`] = range[0]
        if (Array.isArray(range) && range[1]) params[`${f.key}To`] = range[1]
        continue
      }
      if (f.type === 'date') {
        if (v !== undefined && v !== null && v !== '') params[f.key] = v as string
        continue
      }
      if (f.multiple && Array.isArray(v) && v.length) {
        params[f.key] = v.join(',')
        continue
      }
      if (v !== undefined && v !== null && v !== '') params[f.key] = v as string | number
    }
    for (const [k, v] of Object.entries(props.config.listParams ?? {})) {
      if (v !== undefined && v !== null && v !== '') params[k] = v
    }
    for (const [k, v] of Object.entries(props.extraQuery ?? {})) {
      if (v !== undefined && v !== null && v !== '') params[k] = v as string | number | boolean
    }
    if (sortField.value && sortOrder.value) {
      params.sortBy = sortField.value
      params.sortOrder = sortOrder.value
    }
    const { data } = await http.get(url, { params })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  void nextTick().then(() => {
    page.value = 1
    onClearSelection()
    load()
  })
}

function onReset() {
  keyword.value = ''
  selectedMoreSearchKeys.value = []
  moreSearchLabels.value = {}
  for (const f of moreSearchFields.value) {
    moreSearchValues.value[f.key] = ''
  }
  for (const f of props.config.listFilters ?? []) {
    filterValues[f.key] = undefined
  }
  sortField.value = null
  sortOrder.value = null
  page.value = 1
  onClearSelection()
  load()
}

function openForm(row?: Record<string, unknown>, mode: 'create' | 'edit' | 'view' = 'create') {
  form.value = row ? { ...row } : { ...(props.defaultFormValues ?? {}) }
  formMode.value = mode
  formTitle.value = mode === 'view' ? '查看' : mode === 'edit' ? '编辑' : '新增'
  formVisible.value = true
}

/** 空串 UUID 外键转为 null（如上级分类清空 = 一级） */
function normalizeSaveBody(src: Record<string, unknown>) {
  const body: Record<string, unknown> = { ...src }
  for (const [k, v] of Object.entries(body)) {
    if (!(k === 'id' || k.endsWith('_id') || k.endsWith('_by'))) continue
    if (v === undefined || v === '') body[k] = null
  }
  return body
}

function isApiOk(data: { code?: number; success?: boolean } | undefined) {
  if (!data) return false
  if (typeof data.code === 'number') return data.code === 0
  if (typeof data.success === 'boolean') return data.success
  return true
}

async function save() {
  if (formMode.value === 'view') return
  const missing = formFields.value.filter(
    (f) => f.required && (form.value[f.prop] === undefined || form.value[f.prop] === null || form.value[f.prop] === '')
  )
  if (missing.length) {
    ElMessage.warning(`请填写：${missing.map((f) => f.label).join('、')}`)
    return
  }
  try {
    const payload = normalizeSaveBody(form.value)
    const id = payload.id
    let data: { code?: number; success?: boolean; message?: string } | undefined
    if (props.config.saveUrl) {
      data = (await http.post(props.config.saveUrl, payload)).data
    } else if (id) {
      data = (await http.put(`${props.config.apiBase}/${props.config.table}/${id}`, payload)).data
    } else {
      data = (await http.post(`${props.config.apiBase}/${props.config.table}`, payload)).data
    }
    // 后端业务失败常仍返回 HTTP 200 + code!=0，必须校验，否则会出现“成功但库中无数据”
    if (!isApiOk(data)) {
      ElMessage.error(data?.message || '保存失败')
      return
    }
    formVisible.value = false
    ElMessage.success('保存成功')
    emit('saved', { ...form.value })
    load()
  } catch (e: unknown) {
    const err = e as { isBizError?: boolean; message?: string; response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || err?.message || '保存失败')
  }
}

function onImportSuccess() {
  emit('imported')
  load()
}

function onAdd() {
  if (props.detailMode) emit('add')
  else openForm(undefined, 'create')
}

function onEdit(row: Record<string, unknown>) {
  if (props.detailMode) emit('detail', row)
  else openForm(row, 'edit')
}

function onView(row: Record<string, unknown>) {
  openForm(row, 'view')
}

function openChangeLog() {
  changeLogEntityId.value = String(form.value.id ?? '')
  changeLogVisible.value = true
}

async function remove(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确认删除该记录？', '删除', { type: 'warning' })
    const url = props.deleteUrl
      ? `${props.deleteUrl}/${row.id}`
      : `${props.config.apiBase}/${props.config.table}/${row.id}`
    await http.delete(url)
    emit('deleted', row)
    ElMessage.success('已删除')
    load()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(msg || '删除失败')
    }
  }
}

function onSelectionChange(selection: Record<string, unknown>[]) {
  selectedRows.value = selection
  syncFromTable(selection)
}

function onClearSelection() {
  clearAll(tableRef.value)
}

function buildFilterQueryParams(): Record<string, string> {
  const params: Record<string, string> = {}
  if (!moreSearchFields.value.length && keyword.value) params.keyword = keyword.value
  for (const f of moreSearchFields.value) {
    const v = moreSearchValues.value[f.key]?.trim()
    if (!v) continue
    params[f.key] = v
  }
  if (props.config.listMode) params.mode = props.config.listMode
  for (const f of props.config.listFilters ?? []) {
    const v = filterValues[f.key]
    if (f.type === 'daterange') {
      const range = v as string[] | undefined
      if (Array.isArray(range) && range[0]) params[`${f.key}From`] = range[0]
      if (Array.isArray(range) && range[1]) params[`${f.key}To`] = range[1]
      continue
    }
    if (f.multiple && Array.isArray(v) && v.length) {
      params[f.key] = v.join(',')
      continue
    }
    if (v !== undefined && v !== null && v !== '') params[f.key] = String(v)
  }
  for (const [k, v] of Object.entries(props.config.listParams ?? {})) {
    if (v !== undefined && v !== null && v !== '') params[k] = String(v)
  }
  return params
}

async function openPinyinDialog() {
  const scope = await promptPinyinScope(selectedCount.value)
  if (!scope || !assertScopeSelection(scope, selectedCount.value)) return
  try {
    const ok = await executePinyinGenerate(pinyinCodeUrl.value, scope, {
      selectedIds: selectedIds(),
      keyword: keyword.value || undefined
    })
    if (ok) {
      onClearSelection()
      load()
    }
  } catch {
    ElMessage.error('生成拼音简码失败')
  }
}

async function exportCsv() {
  const scope = await promptListActionScope(selectedCount.value, '导出')
  if (!scope || !assertScopeSelection(scope, selectedCount.value)) return
  try {
    const params = new URLSearchParams()
    if (scope === 'selected') {
      params.set('ids', selectedIds().join(','))
    } else {
      for (const [k, v] of Object.entries(buildFilterQueryParams())) {
        params.set(k, v)
      }
    }
    const qs = params.toString()
    const url = qs ? `${exportUrl.value}?${qs}` : exportUrl.value
    await downloadApiFile(url, `${props.config.table}_export.csv`)
  } catch {
    ElMessage.error('导出失败')
  }
}

function onRowDblClick(row: Record<string, unknown>) {
  emit('detail', row)
}

watch(() => props.config, load, { deep: true })

let initialized = false
onMounted(async () => {
  const listDictTypes = (listFields.value ?? [])
    .map((f) => f.dictType)
    .concat((props.config.listFilters ?? []).map((f) => f.dictType))
  await preloadDictTypes(listDictTypes)
  for (const f of props.config.listFilters ?? []) {
    if (f.dictType) {
      const all = await loadDict(f.dictType)
      filterOptions[f.key] = f.dictValues?.length
        ? all.filter((o) => f.dictValues!.includes(o.value))
        : all
    } else if (f.options?.length) {
      filterOptions[f.key] = f.options
    }
  }
  await loadRefLabels()
  await load()
  initialized = true
})
onActivated(() => {
  if (initialized) load()
})

watch(
  () => props.extraQuery,
  () => {
    if (!initialized) return
    page.value = 1
    onClearSelection()
    void load()
  },
  { deep: true }
)

function getSelectedRows() {
  return selectedRows.value
}

defineExpose({ load, getSelectedRows, selectedCount, selectedIds })
</script>

<style scoped>
:deep(.filter-item) {
  width: 160px;
}
:deep(.filter-ref) {
  width: 180px;
}
:deep(.filter-daterange) {
  width: 260px;
}
:deep(.filter-date) {
  width: 140px;
}
</style>
