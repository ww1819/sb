<template>
  <SystemPageCard
    title="用户管理"
    subtitle="维护医院用户账号、角色与权限"
    :loading="loading"
    show-pager
    v-model:page="page"
    v-model:size="size"
    :total="total"
    @page-change="onPageChange"
  >
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
    </template>

    <template #filterBar>
      <PageFilterBar v-model:keyword="keyword" @search="search" @reset="resetSearch">
        <template #filters>
          <el-select
            v-model="filterActive"
            clearable
            placeholder="启用状态"
            style="width: 120px"
            @change="search"
          >
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </template>
      </PageFilterBar>
    </template>

    <el-table
      v-if="rows.length || loading"
      :data="rows"
      row-key="id"
      stripe
      class="system-table user-table"
      style="width: 100%"
      :height="tableHeight"
    >
      <el-table-column type="selection" width="48" fixed="left" reserve-selection />
      <el-table-column label="序号" width="64" fixed="left" align="center">
        <template #default="{ $index }">{{ (page - 1) * size + $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="用户" min-width="180" fixed="left">
        <template #default="{ row }">
          <div class="user-cell">
            <div class="user-avatar" :style="avatarStyle(row.real_name || row.username)">
              {{ avatarText(row.real_name || row.username) }}
            </div>
            <div class="user-cell-info">
              <div class="user-cell-name">{{ row.real_name || '-' }}</div>
              <div class="user-cell-sub">@{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="employee_no" label="工号" width="100">
        <template #default="{ row }">
          <span class="text-muted">{{ row.employee_no || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机" width="130">
        <template #default="{ row }">
          <span class="text-muted">{{ row.phone || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="dept_name" label="科室" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="text-muted">{{ row.dept_name || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="role_name" label="角色" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="text-muted">{{ row.role_name || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="permission_mode" label="权限模式" width="110">
        <template #default="{ row }">
          <el-tag
            :type="row.permission_mode === 'custom' ? 'warning' : 'primary'"
            effect="light"
            size="small"
          >
            {{ row.permission_mode === 'custom' ? '自定义' : '跟随角色' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="is_active" label="状态" width="90">
        <template #default="{ row }">
          <span class="status-dot-wrap">
            <span class="status-dot" :class="row.is_active ? 'status-dot--success' : 'status-dot--disabled'" />
            {{ row.is_active ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="last_login_at" label="最后登录" min-width="160">
        <template #default="{ row }">
          <span class="text-time">{{ formatTime(row.last_login_at) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="300"
        fixed="right"
        header-align="center"
        align="center"
        class-name="col-operations"
      >
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openChangeLog(row)">变更记录</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openRole(row)">分配角色</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => onUserAction(cmd, row)">
              <el-button link type="primary">
                更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="perm">调整权限</el-dropdown-item>
                  <el-dropdown-item command="resetRole">从角色恢复</el-dropdown-item>
                  <el-dropdown-item command="resetPwd">重置密码</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="!rows.length && !loading" #empty>
      <el-empty description="暂无用户">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
      </el-empty>
    </template>

    <el-dialog
      v-model="formVisible"
      :title="formTitle"
      width="640px"
      destroy-on-close
      class="user-form-dialog"
    >
      <el-form :model="form" label-position="top">
        <div class="form-section-title">账号信息</div>
        <el-row :gutter="16" class="dialog-form-grid">
          <el-col :span="12">
            <el-form-item label="用户名" required>
              <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col v-if="!form.id" :span="12">
            <el-form-item label="密码">
              <el-input v-model="form.password" placeholder="默认 123456" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch v-model="form.is_active" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">基本信息</div>
        <el-row :gutter="16" class="dialog-form-grid">
          <el-col :span="12">
            <el-form-item label="姓名" required>
              <el-input v-model="form.real_name" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工号">
              <el-input v-model="form.employee_no" placeholder="员工工号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="form.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="电子邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="科室">
              <el-select v-model="form.dept_id" filterable clearable placeholder="选择科室" style="width:100%">
                <el-option v-for="d in flatDepts" :key="d.id" :label="d.dept_name" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="!form.id">
          <div class="form-section-title">初始角色</div>
          <el-row :gutter="16" class="dialog-form-grid">
            <el-col :span="24">
              <el-form-item label="角色">
                <el-select v-model="form.role_id" filterable clearable placeholder="可选，创建后也可分配" style="width:100%">
                  <el-option v-for="r in roleOptions" :key="r.id" :label="r.role_name" :value="r.id" />
                </el-select>
              </el-form-item>
              <p class="permission-hint">选择角色后将自动同步该角色的权限到用户</p>
            </el-col>
          </el-row>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingForm" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="480px" destroy-on-close>
      <div v-if="currentUser" class="drawer-user-bar">
        <div class="user-avatar" :style="avatarStyle(currentUser.real_name || currentUser.username)">
          {{ avatarText(currentUser.real_name || currentUser.username) }}
        </div>
        <div class="user-cell-info">
          <div class="user-cell-name">{{ currentUser.real_name }}</div>
          <div class="user-cell-sub">@{{ currentUser.username }}</div>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item label="角色" required>
          <el-select v-model="selectedRoleId" placeholder="选择角色" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.role_name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步权限">
          <el-switch v-model="syncOnAssign" />
          <p class="permission-hint">开启后立即复制角色权限到用户</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRole" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <AppModal
      v-model="permVisible"
      title="调整权限"
      size="lg"
    >
      <div v-if="currentUser" class="drawer-user-bar">
        <div class="user-avatar" :style="avatarStyle(currentUser.real_name || currentUser.username)">
          {{ avatarText(currentUser.real_name || currentUser.username) }}
        </div>
        <div class="user-cell-info">
          <div class="user-cell-name">{{ currentUser.real_name }}</div>
          <div class="drawer-user-meta">
            <span class="user-cell-sub">@{{ currentUser.username }}</span>
            <el-tag v-if="currentUser.role_name" size="small" type="info" effect="light">
              {{ currentUser.role_name }}
            </el-tag>
            <el-tag
              size="small"
              effect="light"
              :type="currentUser.permission_mode === 'custom' ? 'warning' : 'primary'"
            >
              {{ currentUser.permission_mode === 'custom' ? '自定义权限' : '跟随角色' }}
            </el-tag>
          </div>
        </div>
      </div>
      <PermissionEditor ref="permEditorRef" :value="permValue" />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPerm" @click="savePerm">保存权限</el-button>
      </template>
    </AppModal>
    <EntityChangeHistoryDrawer v-model="changeLogVisible" entity-type="sys_user" :entity-id="changeLogId" />
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowDown, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import { fetchPage, usePagedList } from '@/composables/usePagedList'
import PermissionEditor, { type PermissionModel } from '@/components/PermissionEditor.vue'
import AppModal from '@/components/AppModal.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()

const filterActive = ref<boolean | ''>('')
const roleOptions = ref<any[]>([])
const flatDepts = ref<any[]>([])
const formVisible = ref(false)
const roleVisible = ref(false)
const permVisible = ref(false)
const changeLogVisible = ref(false)
const changeLogId = ref('')
const formTitle = ref('新建用户')
const form = ref<any>({ is_active: true })
const currentUser = ref<any>(null)

function openChangeLog(row: any) {
  changeLogId.value = String(row.id)
  changeLogVisible.value = true
}
const selectedRoleId = ref('')
const syncOnAssign = ref(true)
const permValue = ref<PermissionModel>()
const permEditorRef = ref<InstanceType<typeof PermissionEditor>>()
const savingForm = ref(false)
const savingRole = ref(false)
const savingPerm = ref(false)

const { rows, total, page, size, keyword, loading, load, search, onPageChange } = usePagedList((params) => {
  if (filterActive.value !== '') params.set('isActive', String(filterActive.value))
  return fetchPage('/system/users/page', params)
})

function resetSearch() {
  keyword.value = ''
  filterActive.value = ''
  search()
}

function avatarText(name?: string) {
  const n = (name || 'U').trim()
  return n.slice(0, 1).toUpperCase()
}

function avatarHue(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return Math.abs(hash % 360)
}

function avatarStyle(name?: string) {
  const n = name || 'User'
  const h = avatarHue(n)
  return {
    background: `linear-gradient(135deg, hsl(${h}, 65%, 55%), hsl(${(h + 40) % 360}, 70%, 45%))`
  }
}

function formatTime(v?: string) {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(async () => {
  await load()
  const [roles, depts] = await Promise.all([http.get('/system/roles'), http.get('/system/departments')])
  if (roles.data.code === 0) roleOptions.value = roles.data.data
  if (depts.data.code === 0) flatDepts.value = depts.data.data
})

function openCreate() {
  formTitle.value = '新建用户'
  form.value = { username: '', password: '123456', real_name: '', is_active: true }
  formVisible.value = true
}

function openEdit(row: any) {
  formTitle.value = '编辑用户'
  form.value = { ...row, is_active: row.is_active ?? true }
  formVisible.value = true
}

async function saveForm() {
  if (!form.value.username || !form.value.real_name) {
    ElMessage.warning('请填写用户名和姓名')
    return
  }
  savingForm.value = true
  try {
    if (form.value.id) {
      const { data } = await http.put(`/system/users/${form.value.id}`, form.value)
      if (data.code !== 0) {
        ElMessage.error(data.message || '保存失败')
        return
      }
    } else {
      const { data } = await http.post('/system/users', form.value)
      if (data.code !== 0) {
        ElMessage.error(data.message || '创建失败')
        return
      }
      if (form.value.role_id) {
        const roleRes = await http.put(`/system/users/${data.data.id}/role`, {
          role_id: form.value.role_id,
          syncPermissions: true
        })
        if (roleRes.data.code !== 0) {
          ElMessage.warning('用户已创建，但角色分配失败：' + (roleRes.data.message || ''))
        }
      }
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingForm.value = false
  }
}

function openRole(row: any) {
  currentUser.value = row
  selectedRoleId.value = row.role_id || ''
  syncOnAssign.value = true
  roleVisible.value = true
}

async function saveRole() {
  if (!currentUser.value || !selectedRoleId.value) {
    ElMessage.warning('请选择角色')
    return
  }
  savingRole.value = true
  try {
    await http.put(`/system/users/${currentUser.value.id}/role`, {
      role_id: selectedRoleId.value,
      syncPermissions: syncOnAssign.value
    })
    ElMessage.success('角色已分配')
    roleVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '分配失败')
  } finally {
    savingRole.value = false
  }
}

async function openPerm(row: any) {
  currentUser.value = row
  const { data } = await http.get(`/system/users/${row.id}/permissions`)
  if (data.code === 0) {
    permValue.value = data.data.permissions as PermissionModel
    permVisible.value = true
  }
}

async function savePerm() {
  if (!currentUser.value || !permEditorRef.value) return
  savingPerm.value = true
  try {
    const perms = permEditorRef.value.getPermissions()
    await http.put(`/system/users/${currentUser.value.id}/permissions`, perms)
    ElMessage.success('用户权限已保存')
    permVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingPerm.value = false
  }
}

async function resetFromRole(row: any) {
  await ElMessageBox.confirm('将用当前角色权限覆盖该用户的自定义权限，是否继续？', '从角色恢复', { type: 'warning' })
  await http.post(`/system/users/${row.id}/reset-from-role`)
  ElMessage.success('已从角色恢复权限')
  load()
}

async function resetPwd(row: any) {
  const { value } = await ElMessageBox.prompt('输入新密码', '重置密码', { inputValue: '123456' })
  if (value) {
    await http.post(`/system/users/${row.id}/reset-password`, { password: value })
    ElMessage.success('密码已重置')
  }
}

function onUserAction(cmd: string, row: any) {
  if (cmd === 'perm') openPerm(row)
  else if (cmd === 'resetRole') resetFromRole(row)
  else if (cmd === 'resetPwd') resetPwd(row)
}
</script>

<style scoped>
.user-table :deep(.col-operations .cell) {
  overflow: visible;
  padding-left: 8px;
  padding-right: 8px;
}

.user-table .table-actions {
  gap: 0;
  width: 100%;
  justify-content: center;
}

.user-table .table-actions :deep(.el-button) {
  padding-left: 4px;
  padding-right: 4px;
  margin-left: 0;
}
</style>
