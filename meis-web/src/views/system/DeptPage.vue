<template>
  <SystemPageCard
    title="科室维护"
    subtitle="维护科室与临床属性"
    :loading="loading"
    show-pager
    v-model:page="page"
    v-model:size="size"
    :total="filteredTotal"
    @page-change="onPageChange"
  >
    <template #filterBar>
      <PageFilterBar v-model:keyword="keyword" placeholder="关键词搜索" @search="applyFilter" @reset="resetFilter">
        <template #actions>
          <el-button type="primary" @click="openForm()">新增科室</el-button>
          <el-button @click="importVisible = true">导入</el-button>
          <el-button @click="exportCsv">导出</el-button>
          <el-button @click="openPinyinDialog">生成简码</el-button>
        </template>
      </PageFilterBar>
    </template>

    <el-table
      ref="tableRef"
      :data="pagedList"
      border
      stripe
      row-key="id"
      class="system-table dept-tree-table"
      :height="tableHeight"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column prop="dept_code" label="科室编码" width="100" />
      <el-table-column prop="dept_name" label="科室名称" />
      <el-table-column prop="pinyin_code" label="拼音简码" width="100" />
      <el-table-column prop="campus_name" label="院区" width="120" />
      <el-table-column prop="is_clinical" label="临床科室" width="100">
        <template #default="{ row }">{{ row.is_clinical ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="is_active" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.is_active ? 'success' : 'info'" size="small">{{ row.is_active ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openChangeLog(row)">变更记录</el-button>
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <AppModal
      v-model="visible"
      :title="form.id ? '编辑科室' : '新增科室'"
      size="sm"
      placement="right"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="科室编码" required><el-input v-model="form.dept_code" maxlength="3" /></el-form-item>
        <el-form-item label="科室名称" required><el-input v-model="form.dept_name" /></el-form-item>
        <el-form-item label="拼音简码"><el-input v-model="form.pinyin_code" placeholder="留空则按名称自动生成" /></el-form-item>
        <el-form-item label="上级科室">
          <el-select v-model="form.parent_id" clearable filterable style="width:100%">
            <el-option v-for="d in list" :key="d.id" :label="d.dept_name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属院区">
          <el-select v-model="form.campus_id" clearable filterable style="width:100%">
            <el-option v-for="c in campuses" :key="c.id" :label="c.campus_name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="临床科室"><el-switch v-model="form.is_clinical" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort_order" :min="0" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.is_active" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>

    <EntityChangeHistoryDrawer v-model="changeLogVisible" entity-type="department" :entity-id="changeLogId" />
    <ImportDialog
      v-model="importVisible"
      title="科室导入"
      import-url="/system/departments/import"
      template-url="/system/departments/import/template"
      template-filename="department_import_template.xlsx"
      @success="load"
    />
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import AppModal from '@/components/AppModal.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'
import { downloadApiFile } from '@/utils/fileDownload'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { promptListActionScope, assertScopeSelection } from '@/composables/useListActionScope'
import { executePinyinGenerate, promptPinyinScope } from '@/composables/usePinyinGenerate'

const tableHeight = useSystemTableHeight()

const list = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const campuses = ref<any[]>([])
const visible = ref(false)
const importVisible = ref(false)
const changeLogVisible = ref(false)
const changeLogId = ref('')
const form = ref<any>({ is_active: true, is_clinical: false, sort_order: 0 })
const tableRef = ref()
const page = ref(1)
const size = ref(20)

const {
  selectedCount,
  syncFromTable,
  selectedIds,
  clearAll
} = useCrossPageSelection()

function openChangeLog(row: any) {
  changeLogId.value = String(row.id)
  changeLogVisible.value = true
}

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((r) =>
    [r.dept_code, r.dept_name, r.pinyin_code, r.campus_name].some((v) => String(v || '').toLowerCase().includes(kw))
  )
})

const filteredTotal = computed(() => filteredList.value.length)

const pagedList = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredList.value.slice(start, start + size.value)
})

watch(filteredTotal, (total) => {
  const maxPage = Math.max(1, Math.ceil(total / size.value) || 1)
  if (page.value > maxPage) page.value = maxPage
})

onMounted(async () => {
  await load()
  const { data } = await http.get('/system/campuses')
  if (data.code === 0) campuses.value = data.data
})

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/departments')
    if (data.code === 0) list.value = data.data
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: any[]) {
  syncFromTable(rows)
}

function onPageChange() {
  // 分页切换后表格勾选由 reserve-selection 维持
}

function applyFilter() {
  page.value = 1
  clearAll(tableRef.value)
}

function resetFilter() {
  keyword.value = ''
  page.value = 1
  clearAll(tableRef.value)
}

function openForm(row?: any) {
  form.value = row ? { ...row } : { dept_code: '', dept_name: '', is_active: true, is_clinical: false, sort_order: 0 }
  visible.value = true
}

async function save() {
  if (form.value.id) await http.put(`/system/departments/${form.value.id}`, form.value)
  else await http.post('/system/departments', form.value)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该科室？删除后不可恢复。', '删除科室', { type: 'warning' })
  await http.delete(`/system/departments/${row.id}`)
  load()
}

async function exportCsv() {
  const scope = await promptListActionScope(selectedCount.value, '导出')
  if (!scope || !assertScopeSelection(scope, selectedCount.value)) return
  try {
    const params = new URLSearchParams()
    if (scope === 'selected') params.set('ids', selectedIds().join(','))
    else if (keyword.value) params.set('keyword', keyword.value)
    const qs = params.toString()
    await downloadApiFile(
      qs ? `/system/departments/export?${qs}` : '/system/departments/export',
      'department_export.csv'
    )
  } catch {
    ElMessage.error('导出失败')
  }
}

async function openPinyinDialog() {
  const scope = await promptPinyinScope(selectedCount.value)
  if (!scope || !assertScopeSelection(scope, selectedCount.value)) return
  try {
    const ok = await executePinyinGenerate('/system/departments/generate-pinyin', scope, {
      selectedIds: selectedIds(),
      keyword: keyword.value || undefined
    })
    if (ok) {
      clearAll(tableRef.value)
      load()
    }
  } catch {
    ElMessage.error('生成拼音简码失败')
  }
}
</script>
