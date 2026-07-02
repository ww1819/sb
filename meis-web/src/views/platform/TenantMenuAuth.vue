<template>
  <SystemPageCard title="租户菜单授权" subtitle="为租户勾选可访问的平台菜单" :loading="loading">
    <el-row :gutter="16">
      <el-col :span="7">
        <el-card shadow="never" class="tenant-select-card">
          <template #header>选择租户</template>
          <el-select
            v-model="tenantId"
            placeholder="选择租户"
            filterable
            style="width:100%"
            :disabled="!tenants.length"
            @change="loadMenus"
          >
            <el-option
              v-for="t in tenants"
              :key="String(t.id)"
              :label="`${t.tenant_name} (${t.tenant_code})`"
              :value="String(t.id)"
            />
          </el-select>
          <p class="permission-hint" style="margin-top:12px">
            左侧选择租户后，右侧勾选该租户可使用的功能菜单，保存后立即生效。
          </p>
          <el-empty v-if="!tenants.length" description="暂无租户，请先开户" :image-size="64" />
        </el-card>
      </el-col>
      <el-col :span="17">
        <div class="auth-panel">
          <p class="permission-hint">勾选菜单节点；父节点半选表示部分子菜单已授权。</p>
          <div class="permission-tree-box">
            <el-tree
              ref="treeRef"
              :data="menuTree"
              show-checkbox
              node-key="menu_code"
              :props="{ label: 'menu_name', children: 'children' }"
              default-expand-all
            />
          </div>
          <div class="auth-footer">
            <el-button type="primary" :disabled="!tenantId" :loading="saving" @click="save">保存授权</el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import http from '@/api/http'
import { ElMessage } from 'element-plus'
import SystemPageCard from '@/components/system/SystemPageCard.vue'

interface TenantRow {
  id: string
  tenant_code: string
  tenant_name: string
}

const route = useRoute()
const tenants = ref<TenantRow[]>([])
const tenantId = ref('')
const menuTree = ref<Record<string, unknown>[]>([])
const treeRef = ref()
const loading = ref(false)
const saving = ref(false)

async function loadTenants() {
  loading.value = true
  try {
    const { data } = await http.get('/tenant/list')
    if (data.code === 0) {
      tenants.value = (data.data ?? []).map((t: TenantRow) => ({
        ...t,
        id: String(t.id)
      }))
      const fromQuery = route.query.tenantId ? String(route.query.tenantId) : ''
      if (fromQuery && tenants.value.some((t) => t.id === fromQuery)) {
        tenantId.value = fromQuery
        await loadMenus()
      }
    } else {
      ElMessage.error(data.message || '加载租户列表失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载租户列表失败')
  } finally {
    loading.value = false
  }
}

async function loadMenuTree() {
  try {
    const { data } = await http.get('/system/platform/menus')
    if (data.code === 0) {
      menuTree.value = data.data ?? []
    } else {
      ElMessage.error(data.message || '加载平台菜单失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载平台菜单失败')
  }
}

async function loadMenus() {
  if (!tenantId.value) return
  try {
    const { data } = await http.get(`/tenant/${tenantId.value}/menus`)
    if (data.code === 0) {
      treeRef.value?.setCheckedKeys(data.data ?? [])
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载租户菜单失败')
  }
}

async function save() {
  if (!tenantId.value) {
    ElMessage.warning('请先选择租户')
    return
  }
  const keys = treeRef.value?.getCheckedKeys(true) ?? []
  saving.value = true
  try {
    const { data } = await http.post(`/tenant/${tenantId.value}/menus`, keys)
    if (data.code === 0) {
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(data.message || '保存失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadTenants()
  loadMenuTree()
})
</script>

<style scoped>
.tenant-select-card {
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
}

.tenant-select-card :deep(.el-card__header) {
  padding: 12px 16px;
  font-weight: 600;
  background: #fafafa;
}

.auth-panel {
  display: flex;
  flex-direction: column;
  min-height: 400px;
}

.auth-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--meis-border-light);
}
</style>
