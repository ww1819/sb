<template>
  <SystemPageCard title="库房管理" subtitle="维护库房与院区、科室关联" :loading="loading" show-search @search="applyFilter" @reset="resetFilter" v-model:keyword="keyword">
    <template #actions>
      <el-button type="primary" @click="openForm()">新增库房</el-button>
    </template>
    <el-table :data="filteredList" border stripe class="system-table" :height="tableHeight">
      <el-table-column prop="warehouse_code" label="编码" width="100" />
      <el-table-column prop="warehouse_name" label="名称" />
      <el-table-column prop="campus_name" label="院区" width="120" />
      <el-table-column prop="dept_name" label="科室" width="120" />
      <el-table-column prop="address" label="地址" show-overflow-tooltip />
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
    <el-dialog v-model="visible" title="库房" width="520px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="编码" required><el-input v-model="form.warehouse_code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.warehouse_name" /></el-form-item>
        <el-form-item label="院区">
          <el-select v-model="form.campus_id" clearable style="width:100%">
            <el-option v-for="c in campuses" :key="c.id" :label="c.campus_name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科室">
          <el-select v-model="form.dept_id" clearable filterable style="width:100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.dept_name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
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
const depts = ref<any[]>([])
const visible = ref(false)
const form = ref<any>({ is_active: true, sort_order: 0 })

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((r) =>
    [r.warehouse_code, r.warehouse_name, r.campus_name, r.dept_name, r.address].some((v) =>
      String(v || '').toLowerCase().includes(kw)
    )
  )
})

onMounted(async () => {
  await load()
  const [c, d] = await Promise.all([http.get('/system/campuses'), http.get('/system/departments')])
  if (c.data.code === 0) campuses.value = c.data.data
  if (d.data.code === 0) depts.value = d.data.data
})

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/warehouses')
    if (data.code === 0) list.value = data.data
  } finally {
    loading.value = false
  }
}

function applyFilter() {}
function resetFilter() { keyword.value = '' }

function openForm(row?: any) {
  form.value = row ? { ...row } : { warehouse_code: '', warehouse_name: '', is_active: true, sort_order: 0 }
  visible.value = true
}

async function save() {
  if (form.value.id) await http.put(`/system/warehouses/${form.value.id}`, form.value)
  else await http.post('/system/warehouses', form.value)
  ElMessage.success('保存成功')
  visible.value = false
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该库房？删除后不可恢复。', '删除库房', { type: 'warning' })
  await http.delete(`/system/warehouses/${row.id}`)
  load()
}
</script>
