<template>
  <SystemPageCard title="院区管理" subtitle="维护医院院区基础信息" :loading="loading" show-search @search="applyFilter" @reset="resetFilter" v-model:keyword="keyword">
    <template #actions>
      <el-button type="primary" @click="openForm()">新增院区</el-button>
    </template>
    <el-table :data="filteredList" border stripe class="system-table" :height="tableHeight">
      <el-table-column label="序号" width="64" align="center">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="campus_code" label="院区编码" width="100" />
      <el-table-column prop="campus_name" label="院区名称" />
      <el-table-column prop="address" label="地址" show-overflow-tooltip />
      <el-table-column prop="contact_phone" label="联系电话" width="130" />
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
    <el-dialog v-model="visible" :title="form.id ? '编辑院区' : '新增院区'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="院区编码" required><el-input v-model="form.campus_code" maxlength="1" /></el-form-item>
        <el-form-item label="院区名称" required><el-input v-model="form.campus_name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contact_phone" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.is_active" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <EntityChangeHistoryDrawer v-model="changeLogVisible" entity-type="campus" :entity-id="changeLogId" />
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()

const list = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const visible = ref(false)
const changeLogVisible = ref(false)
const changeLogId = ref('')
const form = ref<any>({ is_active: true })

function openChangeLog(row: any) {
  changeLogId.value = String(row.id)
  changeLogVisible.value = true
}

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((r) =>
    [r.campus_code, r.campus_name, r.address, r.contact_phone].some((v) => String(v || '').toLowerCase().includes(kw))
  )
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/campuses')
    if (data.code === 0) list.value = data.data
  } finally {
    loading.value = false
  }
}

function applyFilter() {}
function resetFilter() { keyword.value = '' }

function openForm(row?: any) {
  form.value = row ? { ...row } : { campus_code: '', campus_name: '', is_active: true }
  visible.value = true
}

function upsertCampus(row: any) {
  const idx = list.value.findIndex((r) => r.id === row.id)
  if (idx >= 0) list.value[idx] = row
  else list.value = [...list.value, row]
  list.value.sort((a, b) => String(a.campus_code).localeCompare(String(b.campus_code)))
}

async function save() {
  const code = String(form.value.campus_code ?? '').trim()
  const name = String(form.value.campus_name ?? '').trim()
  if (!code || !name) {
    ElMessage.warning('请填写院区编码和院区名称')
    return
  }
  const payload = { ...form.value, campus_code: code, campus_name: name }
  try {
    const { data } = form.value.id
      ? await http.put(`/system/campuses/${form.value.id}`, payload)
      : await http.post('/system/campuses', payload)
    if (data.code !== 0) {
      ElMessage.error(data.message || '保存失败')
      return
    }
    if (data.data) upsertCampus(data.data)
    ElMessage.success('保存成功')
    visible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该院区？删除后不可恢复。', '删除院区', { type: 'warning' })
  await http.delete(`/system/campuses/${row.id}`)
  load()
}
</script>
