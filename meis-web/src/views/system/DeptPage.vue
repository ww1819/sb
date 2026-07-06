<template>
  <SystemPageCard title="科室管理" subtitle="维护科室树与临床属性" :loading="loading" show-search @search="applyFilter" @reset="resetFilter" v-model:keyword="keyword">
    <template #actions>
      <el-button type="primary" @click="openForm()">新增科室</el-button>
    </template>
    <el-table :data="filteredList" border stripe row-key="id" default-expand-all class="system-table dept-tree-table" :height="tableHeight">
      <el-table-column prop="dept_code" label="科室编码" width="100" />
      <el-table-column prop="dept_name" label="科室名称" />
      <el-table-column prop="campus_name" label="院区" width="120" />
      <el-table-column prop="is_clinical" label="临床科室" width="100">
        <template #default="{ row }">{{ row.is_clinical ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="is_active" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.is_active ? 'success' : 'info'" size="small">{{ row.is_active ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑科室' : '新增科室'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="科室编码" required><el-input v-model="form.dept_code" maxlength="3" /></el-form-item>
        <el-form-item label="科室名称" required><el-input v-model="form.dept_name" /></el-form-item>
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
    </el-dialog>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()

const list = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const campuses = ref<any[]>([])
const visible = ref(false)
const form = ref<any>({ is_active: true, is_clinical: false, sort_order: 0 })

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((r) =>
    [r.dept_code, r.dept_name, r.campus_name].some((v) => String(v || '').toLowerCase().includes(kw))
  )
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

function applyFilter() {}
function resetFilter() { keyword.value = '' }

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
</script>

