<template>
  <div class="crud-page">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索" clearable style="width: 220px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button v-permission="'add'" type="success" @click="openForm()">新增</el-button>
      <el-button @click="exportCsv">导出</el-button>
      <slot name="toolbar-extra" />
    </div>

    <el-table v-loading="loading" :data="rows" border stripe height="calc(100vh - 260px)" @row-dblclick="onRowDblClick">
      <el-table-column
        v-for="f in listFields"
        :key="f.prop"
        :prop="f.prop"
        :label="f.label"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next, sizes"
      :page-sizes="[10, 20, 50]"
      class="pager"
      @change="load"
    />

    <FormDrawer v-model="formVisible" :title="formTitle" @save="save">
      <el-form label-width="120px">
        <el-form-item v-for="f in formFields" :key="f.prop" :label="f.label" :required="f.required">
          <FieldRenderer v-model="form[f.prop]" :field="f" />
        </el-form-item>
      </el-form>
    </FormDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import FormDrawer from './FormDrawer.vue'
import FieldRenderer from './FieldRenderer.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

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

const schema = computed(() => getSchema(props.config.table))
const listFields = computed(() => {
  const s = schema.value.filter((f) => f.list)
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
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
