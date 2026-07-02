<template>
  <SystemPageCard title="数据字典" subtitle="按类型维护系统字典项">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never" class="dict-type-card">
          <template #header>字典类型</template>
          <el-menu :default-active="activeType" class="dict-type-menu" @select="onTypeSelect">
            <el-menu-item v-for="t in types" :key="t.dict_type" :index="t.dict_type">
              <span class="type-name">{{ t.dict_type }}</span>
              <el-tag size="small" type="info" class="type-count">{{ t.item_count }}</el-tag>
            </el-menu-item>
          </el-menu>
          <el-empty v-if="!types.length" description="暂无字典类型" :image-size="64" />
        </el-card>
      </el-col>
      <el-col :span="18">
        <PageToolbar>
          <template #actions>
            <el-button type="primary" :disabled="!activeType" @click="openForm()">新增字典项</el-button>
          </template>
        </PageToolbar>
        <el-table v-loading="itemsLoading" :data="items" border stripe class="system-table">
          <el-table-column prop="dict_code" label="编码" width="120" />
          <el-table-column prop="dict_label" label="标签" />
          <el-table-column prop="dict_value" label="值" />
          <el-table-column prop="sort_order" label="排序" width="80" />
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
        <el-empty v-if="activeType && !items.length && !itemsLoading" description="该类型下暂无字典项" />
      </el-col>
    </el-row>
    <el-dialog v-model="visible" title="字典项" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型"><el-input v-model="form.dict_type" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="编码" required><el-input v-model="form.dict_code" /></el-form-item>
        <el-form-item label="标签" required><el-input v-model="form.dict_label" /></el-form-item>
        <el-form-item label="值"><el-input v-model="form.dict_value" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort_order" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageToolbar from '@/components/system/PageToolbar.vue'

const types = ref<any[]>([])
const items = ref<any[]>([])
const activeType = ref('')
const itemsLoading = ref(false)
const visible = ref(false)
const form = ref<any>({ is_active: true, sort_order: 0 })

onMounted(async () => {
  const { data } = await http.get('/system/dict/types')
  if (data.code === 0) types.value = data.data
})

async function onTypeSelect(type: string) {
  activeType.value = type
  itemsLoading.value = true
  try {
    const { data } = await http.get(`/system/dict/type/${type}`)
    if (data.code === 0) items.value = data.data
  } finally {
    itemsLoading.value = false
  }
}

function openForm(row?: any) {
  form.value = row ? { ...row } : { dict_type: activeType.value, is_active: true, sort_order: 0 }
  visible.value = true
}

async function save() {
  if (form.value.id) await http.put(`/system/dict/${form.value.id}`, form.value)
  else await http.post('/system/dict', form.value)
  ElMessage.success('保存成功')
  visible.value = false
  onTypeSelect(activeType.value)
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该字典项？', '删除字典项', { type: 'warning' })
  await http.delete(`/system/dict/${row.id}`)
  onTypeSelect(activeType.value)
}
</script>

<style scoped>
.type-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.type-count {
  margin-left: 8px;
}

.dict-type-menu :deep(.el-menu-item) {
  display: flex;
  align-items: center;
}
</style>
