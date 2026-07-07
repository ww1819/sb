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
    <template #actions>
      <el-button v-permission="'add'" type="primary" @click="openForm()">新增</el-button>
      <el-button v-if="showImport" @click="importVisible = true">导入</el-button>
      <el-button @click="exportCsv">导出</el-button>
      <template v-if="showPinyinCode">
        <el-button @click="openPinyinDialog">生成简码</el-button>
      </template>
      <slot name="toolbar-extra" />
    </template>

    <template #filterBar>
      <PageFilterBar
        v-model:keyword="keyword"
        placeholder="关键词搜索"
        @search="onSearch"
        @reset="onReset"
      >
        <template v-if="config.listFilters?.length" #filters>
          <template v-for="f in config.listFilters" :key="f.key">
            <el-select
              v-if="f.dictType"
              v-model="filterValues[f.key]"
              :placeholder="f.label"
              clearable
              class="filter-item"
              @change="onSearch"
            >
              <el-option v-for="o in filterOptions[f.key] ?? []" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input-number
              v-else-if="f.type === 'number'"
              v-model="filterValues[f.key]"
              :placeholder="f.label"
              controls-position="right"
              class="filter-item filter-number"
              @change="onSearch"
            />
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
      <el-table-column v-if="showPinyinCode" type="selection" width="48" fixed="left" reserve-selection />
      <el-table-column
        v-for="f in listFields"
        :key="f.prop"
        :prop="f.prop"
        :label="f.label"
        :align="columnAlign(f.prop, f.type)"
        min-width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <TableCellValue :field="f" :value="row[f.prop]" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <PageEmpty description="暂无数据" />
      </template>
    </el-table>

    <FormDrawer v-model="formVisible" :title="formTitle" size="lg" @save="save">
      <el-form label-width="120px">
        <GroupedFormFields :table="config.table" :model="form" :fields="formFields" />
      </el-form>
    </FormDrawer>

    <ImportDialog
      v-if="showImport"
      v-model="importVisible"
      :title="`${config.title}导入`"
      :import-url="importUrl"
      :template-url="importTemplateUrl"
      :template-filename="`${config.table}_import_template.xlsx`"
      @success="load"
    />
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { downloadApiFile } from '@/utils/fileDownload'
import FormDrawer from './FormDrawer.vue'
import FieldRenderer from './FieldRenderer.vue'
import SystemPageCard from './system/SystemPageCard.vue'
import PageFilterBar from './system/PageFilterBar.vue'
import TableCellValue from './table/TableCellValue.vue'
import PageEmpty from './table/PageEmpty.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getListFields, getSchema } from '@/config/pageSchemas'
import GroupedFormFields from './form/GroupedFormFields.vue'
import ImportDialog from './ImportDialog.vue'
import { columnAlign } from '@/utils/tableCell'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'
import { useDict } from '@/composables/useDict'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { executePinyinGenerate, promptPinyinScope } from '@/composables/usePinyinGenerate'

const props = defineProps<{ config: PageConfig }>()
const emit = defineEmits<{ detail: [row: Record<string, unknown>] }>()
const { loadDict } = useDict()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const filterValues = reactive<Record<string, string | number | undefined>>({})
const filterOptions = reactive<Record<string, { label: string; value: string }[]>>({})
const formVisible = ref(false)
const importVisible = ref(false)
const form = ref<Record<string, unknown>>({})
const formTitle = ref('新增')
const tableRef = ref()
const { selectedCount, syncFromTable, selectedIds, clear: clearSelection } = useCrossPageSelection()

const tableHeight = useSystemTableHeight()

const schema = computed(() => getSchema(props.config.table))
const listFields = computed(() => {
  const s = getListFields(props.config.table)
  if (s.length) return s
  const first = rows.value[0]
  if (!first) return [{ prop: 'id', label: 'id' }]
  return Object.keys(first).filter((k) => !['password_hash'].includes(k)).slice(0, 12).map((k) => ({ prop: k, label: k }))
})
const formFields = computed(() => {
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

async function load() {
  loading.value = true
  try {
    const url = props.config.listPageUrl ?? `${props.config.apiBase}/${props.config.table}/page`
    const params: Record<string, string | number> = {
      page: page.value,
      size: size.value
    }
    if (keyword.value) params.keyword = keyword.value
    for (const f of props.config.listFilters ?? []) {
      const v = filterValues[f.key]
      if (v !== undefined && v !== null && v !== '') params[f.key] = v
    }
    const { data } = await http.get(url, { params })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  clearSelection()
  tableRef.value?.clearSelection()
  load()
}

function onReset() {
  keyword.value = ''
  for (const f of props.config.listFilters ?? []) {
    filterValues[f.key] = undefined
  }
  page.value = 1
  clearSelection()
  tableRef.value?.clearSelection()
  load()
}

function openForm(row?: Record<string, unknown>) {
  form.value = row ? { ...row } : {}
  formTitle.value = row ? '编辑' : '新增'
  formVisible.value = true
}

async function save() {
  const id = form.value.id
  if (id) {
    await http.put(`${props.config.apiBase}/${props.config.table}/${id}`, form.value)
  } else {
    await http.post(`${props.config.apiBase}/${props.config.table}`, form.value)
  }
  formVisible.value = false
  load()
}

async function remove(row: Record<string, unknown>) {
  await http.delete(`${props.config.apiBase}/${props.config.table}/${row.id}`)
  load()
}

function onSelectionChange(selection: Record<string, unknown>[]) {
  syncFromTable(selection)
}

async function openPinyinDialog() {
  const scope = await promptPinyinScope(selectedCount.value)
  if (!scope) return
  try {
    const ok = await executePinyinGenerate(pinyinCodeUrl.value, scope, {
      selectedIds: selectedIds(),
      keyword: keyword.value || undefined
    })
    if (ok) {
      clearSelection()
      tableRef.value?.clearSelection()
      load()
    }
  } catch {
    ElMessage.error('生成拼音简码失败')
  }
}

async function exportCsv() {
  try {
    await downloadApiFile(exportUrl.value, `${props.config.table}_export.csv`)
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
  for (const f of props.config.listFilters ?? []) {
    if (f.dictType) filterOptions[f.key] = await loadDict(f.dictType)
  }
  await load()
  initialized = true
})
onActivated(() => {
  if (initialized) load()
})

defineExpose({ load })
</script>
