<template>
  <SystemPageCard title="租户管理" subtitle="平台侧医院开户与租户维护" :loading="loading">
    <template #actions>
      <el-button type="primary" @click="openCreate">开户</el-button>
    </template>

    <el-table :data="list" border stripe class="system-table" :height="tableHeight">
      <el-table-column prop="tenant_code" label="编码" width="120" />
      <el-table-column prop="tenant_name" label="名称" min-width="160" />
      <el-table-column prop="schema_name" label="Schema" width="160" />
      <el-table-column prop="package_code" label="套餐" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.status || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="goMenuAuth(row)">菜单授权</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建租户（医院开户）" width="520px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户编码" required>
              <el-input v-model="form.tenantCode" placeholder="如 hospital01" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="套餐">
              <el-select v-model="form.packageCode" style="width:100%">
                <el-option v-for="p in packages" :key="p.package_code" :label="p.package_name" :value="p.package_code" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="医院名称" required>
          <el-input v-model="form.tenantName" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCredentials" title="开户成功 — 请妥善保存凭据" width="520px" :close-on-click-modal="false">
      <el-alert type="success" :closable="false" show-icon title="租户私有库已初始化，请将以下账号交给医院租户管理员首次登录。" />
      <el-descriptions :column="1" border style="margin-top:16px">
        <el-descriptions-item label="租户编码">{{ credentials.tenantCode }}</el-descriptions-item>
        <el-descriptions-item label="Schema">{{ credentials.schemaName }}</el-descriptions-item>
        <el-descriptions-item label="套餐">{{ credentials.packageCode }}</el-descriptions-item>
        <el-descriptions-item label="管理员账号">
          <el-tag type="danger">{{ credentials.adminUsername }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="初始密码">
          <el-tag type="danger">{{ credentials.adminPassword }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="showCredentials = false">已记录</el-button>
        <el-button @click="goMenuAuth(credentials)">去菜单授权</el-button>
      </template>
    </el-dialog>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()

const router = useRouter()
const list = ref<any[]>([])
const packages = ref<any[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showCredentials = ref(false)
const creating = ref(false)
const form = reactive({
  tenantCode: '',
  tenantName: '',
  packageCode: 'standard',
  contactName: '',
  contactPhone: ''
})
const credentials = reactive({
  id: '',
  tenantCode: '',
  schemaName: '',
  packageCode: '',
  adminUsername: '',
  adminPassword: ''
})

function statusType(status: string) {
  if (!status) return 'info'
  const s = status.toLowerCase()
  if (s === 'active' || s === '正常') return 'success'
  if (s === 'disabled' || s === '停用') return 'danger'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/tenant/list')
    if (data.code === 0) {
      list.value = data.data ?? []
    } else {
      ElMessage.error(data.message || '加载租户列表失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载租户列表失败，请确认 meis-tenant 服务已启动')
  } finally {
    loading.value = false
  }
}

async function loadPackages() {
  const { data } = await http.get('/tenant/packages')
  if (data.code === 0) packages.value = data.data
}

function openCreate() {
  form.tenantCode = ''
  form.tenantName = ''
  form.packageCode = 'standard'
  form.contactName = ''
  form.contactPhone = ''
  showCreate.value = true
}

async function create() {
  if (!form.tenantCode || !form.tenantName) {
    ElMessage.warning('请填写租户编码和医院名称')
    return
  }
  creating.value = true
  try {
    const { data } = await http.post('/tenant/create', form, { timeout: 180000 })
    if (data.code === 0) {
      showCreate.value = false
      Object.assign(credentials, data.data)
      showCredentials.value = true
      load()
    } else {
      ElMessage.error(data.message || '创建失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function goMenuAuth(row: any) {
  const id = row.id || credentials.id
  router.push({ path: '/platform/tenant-menu', query: id ? { tenantId: id } : {} })
}

onMounted(() => {
  load()
  loadPackages()
})
</script>
