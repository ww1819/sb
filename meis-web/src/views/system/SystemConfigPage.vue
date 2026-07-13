<template>
  <SystemPageCard title="系统配置" subtitle="维护系统运行参数与开关" :loading="loading" show-search @search="applyFilter" @reset="resetFilter" v-model:keyword="keyword">
    <template #actions>
      <el-button type="primary" @click="openForm()">新增配置</el-button>
    </template>
    <el-table :data="filteredList" border stripe class="system-table" :height="tableHeight">
      <el-table-column label="序号" width="64" align="center">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="config_key" label="配置键" width="180" show-overflow-tooltip />
      <el-table-column prop="config_value" label="配置值" show-overflow-tooltip />
      <el-table-column prop="config_type" label="类型" width="100" />
      <el-table-column prop="description" label="说明" show-overflow-tooltip />
      <el-table-column prop="is_system" label="系统内置" width="100">
        <template #default="{ row }">
          <el-tag :type="row.is_system ? 'warning' : 'info'" size="small">{{ row.is_system ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.is_system" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑配置' : '新增配置'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="配置键" required>
          <el-input v-model="form.config_key" :disabled="!!form.id" placeholder="如 device.code.prefix" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.config_value" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.config_type" clearable placeholder="可选" style="width:100%">
            <el-option label="字符串" value="string" />
            <el-option label="数字" value="number" />
            <el-option label="布尔" value="boolean" />
            <el-option label="JSON" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-switch v-model="form.is_system" :disabled="!!form.id" />
        </el-form-item>
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
const visible = ref(false)
const form = ref<any>({ config_type: 'string', is_system: false })

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((r) =>
    [r.config_key, r.config_value, r.config_type, r.description].some((v) => String(v || '').toLowerCase().includes(kw))
  )
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/sys_config/list', { params: { limit: 500 } })
    if (data.code === 0) list.value = data.data ?? []
  } finally {
    loading.value = false
  }
}

function applyFilter() {}
function resetFilter() { keyword.value = '' }

function openForm(row?: any) {
  form.value = row
    ? { ...row }
    : { config_key: '', config_value: '', config_type: 'string', description: '', is_system: false }
  visible.value = true
}

function upsertRow(row: any) {
  const idx = list.value.findIndex((r) => r.id === row.id)
  if (idx >= 0) list.value[idx] = row
  else list.value = [...list.value, row]
  list.value.sort((a, b) => String(a.config_key).localeCompare(String(b.config_key)))
}

async function save() {
  const key = String(form.value.config_key ?? '').trim()
  const value = String(form.value.config_value ?? '').trim()
  if (!key || !value) {
    ElMessage.warning('请填写配置键和配置值')
    return
  }
  const payload = { ...form.value, config_key: key, config_value: value }
  try {
    const { data } = form.value.id
      ? await http.put(`/system/sys_config/${form.value.id}`, payload)
      : await http.post('/system/sys_config', payload)
    if (data.code !== 0) {
      ElMessage.error(data.message || '保存失败')
      return
    }
    if (data.data) upsertRow(data.data)
    ElMessage.success('保存成功')
    visible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm('确定删除该配置？删除后不可恢复。', '删除配置', { type: 'warning' })
  try {
    const { data } = await http.delete(`/system/sys_config/${row.id}`)
    if (data.code !== 0) {
      ElMessage.error(data.message || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}
</script>
