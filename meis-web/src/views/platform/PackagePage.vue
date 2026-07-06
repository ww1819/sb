<template>
  <SystemPageCard title="套餐管理" subtitle="查看平台套餐与包含菜单">
    <el-table :data="packages" border stripe class="system-table" :height="tableHeight" v-loading="loading">
      <el-table-column prop="package_code" label="套餐编码" width="140" />
      <el-table-column prop="package_name" label="名称" min-width="160" />
      <el-table-column prop="max_users" label="用户数上限" width="120" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="loadMenus(row.package_code)">查看菜单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="packageMenus.length" class="menu-tags-panel">
      <div class="menu-tags-title">套餐菜单（{{ activePackage }}）</div>
      <el-tag v-for="m in packageMenus" :key="m" class="menu-tag" type="info">{{ m }}</el-tag>
    </div>
    <el-empty v-else-if="activePackage" description="该套餐暂无菜单配置" :image-size="64" />
  </SystemPageCard>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()

const packages = ref<Record<string, unknown>[]>([])
const packageMenus = ref<string[]>([])
const activePackage = ref('')
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await http.get('/tenant/packages')
    packages.value = data.data ?? []
  } finally {
    loading.value = false
  }
})

async function loadMenus(code: string) {
  activePackage.value = code
  const { data } = await http.get(`/tenant/packages/${code}/menus`)
  packageMenus.value = data.data ?? []
}
</script>

<style scoped>
.menu-tags-panel {
  margin-top: 20px;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
}

.menu-tags-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.menu-tag {
  margin: 4px 8px 4px 0;
}
</style>
