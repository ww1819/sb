<template>
  <SystemPageCard title="角色管理" subtitle="配置角色权限并同步到用户" :loading="loading" show-search @search="applyFilter" @reset="resetFilter" v-model:keyword="keyword">
    <template #actions>
      <el-button type="primary" @click="openCreate">新建角色</el-button>
    </template>
    <el-table :data="filteredRoles" border stripe class="system-table" :height="tableHeight">
      <el-table-column prop="role_code" label="角色编码" width="140" />
      <el-table-column prop="role_name" label="角色名称" width="160" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="sort_order" label="排序" width="80" />
      <el-table-column prop="is_active" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.is_active ? 'success' : 'info'" size="small">{{ row.is_active ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openPerm(row)">授权</el-button>
            <el-button link type="warning" @click="syncPerms(row)">同步到用户</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="formTitle" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.role_code" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.role_name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort_order" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.is_active" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <AppModal
      v-model="permVisible"
      :title="'权限授权 - ' + (currentRole?.role_name || '')"
      size="lg"
    >
      <PermissionEditor ref="permEditorRef" :value="permValue" />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" @click="savePerm">保存权限</el-button>
        <el-button type="warning" @click="syncCurrent">
          <el-icon><Refresh /></el-icon>同步到用户
        </el-button>
      </template>
    </AppModal>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import AppModal from '@/components/AppModal.vue'
import PermissionEditor, { type PermissionModel } from '@/components/PermissionEditor.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()

const roles = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const formVisible = ref(false)
const permVisible = ref(false)
const formTitle = ref('新建角色')
const form = ref<any>({ is_active: true, sort_order: 0 })
const currentRole = ref<any>(null)
const permValue = ref<PermissionModel>()
const permEditorRef = ref<InstanceType<typeof PermissionEditor>>()

const filteredRoles = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return roles.value
  return roles.value.filter((r) =>
    [r.role_code, r.role_name, r.description].some((v) => String(v || '').toLowerCase().includes(kw))
  )
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/roles')
    if (data.code === 0) roles.value = data.data
  } finally {
    loading.value = false
  }
}

function applyFilter() {}
function resetFilter() {
  keyword.value = ''
}

function openCreate() {
  formTitle.value = '新建角色'
  form.value = { role_code: '', role_name: '', description: '', sort_order: 0, is_active: true }
  formVisible.value = true
}

function openEdit(row: any) {
  formTitle.value = '编辑角色'
  form.value = { ...row }
  formVisible.value = true
}

async function saveForm() {
  if (!form.value.role_code || !form.value.role_name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  if (form.value.id) await http.put(`/system/roles/${form.value.id}`, form.value)
  else await http.post('/system/roles', form.value)
  ElMessage.success('保存成功')
  formVisible.value = false
  load()
}

async function openPerm(row: any) {
  currentRole.value = row
  const { data } = await http.get(`/system/roles/${row.id}`)
  if (data.code === 0) {
    permValue.value = parsePerms(data.data.permissions)
    permVisible.value = true
  }
}

function parsePerms(raw: any): PermissionModel {
  if (!raw) return { menus: [], buttons: [], dataScope: 'self', deptIds: [], warehouseIds: [] }
  if (raw.type === 'jsonb' && raw.value) return JSON.parse(raw.value)
  const p = typeof raw === 'string' ? JSON.parse(raw) : raw
  return {
    menus: p.menus || [],
    buttons: p.buttons || [],
    dataScope: p.dataScope || 'self',
    deptIds: p.deptIds || [],
    warehouseIds: p.warehouseIds || []
  }
}

async function savePerm() {
  if (!currentRole.value || !permEditorRef.value) return
  const perms = permEditorRef.value.getPermissions()
  await http.put(`/system/roles/${currentRole.value.id}/permissions`, perms)
  ElMessage.success('权限已保存')
  permVisible.value = false
  load()
}

async function syncPerms(row: any) {
  await ElMessageBox.confirm(
    '将用该角色当前权限覆盖所有绑定此角色的用户，并清除用户单独配置的权限。是否继续？',
    '同步权限到用户',
    { type: 'warning' }
  )
  const { data } = await http.post(`/system/roles/${row.id}/sync-permissions`)
  if (data.code === 0) ElMessage.success(`已同步 ${data.data.updatedCount} 个用户`)
}

async function syncCurrent() {
  if (!currentRole.value) return
  await syncPerms(currentRole.value)
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该角色？', '提示', { type: 'warning' })
  await http.delete(`/system/roles/${row.id}`)
  ElMessage.success('已删除')
  load()
}
</script>
