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
      <el-button @click="exportCsv">导出</el-button>
      <slot name="toolbar-extra" />
    </template>

    <template #filterBar>
      <PageFilterBar
        v-model:keyword="keyword"
        placeholder="关键词搜索"
        @search="onSearch"
        @reset="onReset"
      />
    </template>

    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      class="system-table"
      :height="tableHeight"
      @row-dblclick="onRowDblClick"
    >
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
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import FormDrawer from './FormDrawer.vue'
import FieldRenderer from './FieldRenderer.vue'
import SystemPageCard from './system/SystemPageCard.vue'
import PageFilterBar from './system/PageFilterBar.vue'
import TableCellValue from './table/TableCellValue.vue'
import PageEmpty from './table/PageEmpty.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getListFields, getSchema } from '@/config/pageSchemas'
import GroupedFormFields from './form/GroupedFormFields.vue'
import { columnAlign } from '@/utils/tableCell'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const props = defineProps<{ config: PageConfig }>()
const emit = defineEmits<{ detail: [row: Record<string, unknown>] }>()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const formVisible = ref(false)
const form = ref<Record<string, unknown>>({})
const formTitle = ref('新增')

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

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`${props.config.apiBase}/${props.config.table}/page`, {
      params: { page: page.value, size: size.value, keyword: keyword.value }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  keyword.value = ''
  page.value = 1
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

function exportCsv() {
  window.open(`/api${props.config.apiBase}/${props.config.table}/export`, '_blank')
}

function onRowDblClick(row: Record<string, unknown>) {
  emit('detail', row)
}

watch(() => props.config, load, { deep: true })

let initialized = false
onMounted(async () => {
  await load()
  initialized = true
})
onActivated(() => {
  if (initialized) load()
})

defineExpose({ load })
</script>
