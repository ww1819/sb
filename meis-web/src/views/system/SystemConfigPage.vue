<template>
  <SystemPageCard title="系统配置" subtitle="按参数分类维护编号、名称与多值项" :loading="loading" :show-title="true">
    <div class="config-layout">
      <aside class="config-sider">
        <div class="sider-title">系统参数</div>
        <el-scrollbar class="sider-scroll">
          <div
            v-for="c in categories"
            :key="c.category_code"
            class="sider-item"
            :class="{ active: activeCategory === c.category_code }"
            @click="selectCategory(c.category_code)"
          >
            <el-checkbox
              :model-value="activeCategory === c.category_code"
              @click.stop
              @change="() => selectCategory(c.category_code)"
            />
            <span class="sider-label" :title="`${c.category_name}|${c.category_code}`">
              {{ c.category_name }}|{{ c.category_code }}
            </span>
          </div>
          <el-empty v-if="!categories.length" description="暂无参数分类" :image-size="56" />
        </el-scrollbar>
      </aside>

      <section class="config-main">
        <div class="toolbar">
          <span class="toolbar-label">名称：</span>
          <el-input
            v-model="nameKeyword"
            clearable
            class="name-input"
            placeholder="按名称搜索"
            @keyup.enter="applyFilter"
            @clear="applyFilter"
          />
          <el-button type="primary" :icon="Search" @click="applyFilter">搜索</el-button>
          <el-button :icon="RefreshLeft" @click="resetFilter">重置</el-button>
          <el-button type="primary" :icon="Plus" @click="onAddClick">新增配置</el-button>
        </div>

        <el-table
          :data="filteredList"
          border
          stripe
          class="system-table"
          :height="tableHeight"
        >
          <el-table-column label="操作" width="160" fixed="left">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click="openForm(row, true)">查看</el-button>
                <el-button link type="primary" @click="openForm(row)">编辑</el-button>
                <el-button link type="danger" :disabled="row.is_system" @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="item_code" label="编号" width="90" sortable />
          <el-table-column prop="item_name" label="名称" min-width="160" show-overflow-tooltip sortable />
          <el-table-column prop="value1" label="值1" min-width="80" show-overflow-tooltip />
          <el-table-column prop="value2" label="值2" min-width="80" show-overflow-tooltip />
          <el-table-column prop="value3" label="值3" min-width="80" show-overflow-tooltip />
          <el-table-column prop="value4" label="值4" min-width="80" show-overflow-tooltip />
          <el-table-column prop="value5" label="值5" min-width="80" show-overflow-tooltip />
          <el-table-column prop="value6" label="描述" min-width="140" show-overflow-tooltip />
        </el-table>
      </section>
    </div>

    <el-dialog
      v-model="visible"
      :title="viewOnly ? '查看配置' : form.id ? '编辑配置' : '新增配置'"
      width="560px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" :disabled="viewOnly">
        <el-form-item label="分类编码" required>
          <el-input v-model="form.category_code" maxlength="20" disabled />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="form.category_name" disabled />
        </el-form-item>
        <el-form-item label="编号" required>
          <el-input v-model="form.item_code" maxlength="20" placeholder="如 01" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.item_name" placeholder="参数名称" />
        </el-form-item>
        <el-form-item v-for="n in 5" :key="n" :label="`值${n}`">
          <el-input v-model="form[`value${n}`]" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.value6" type="textarea" :rows="2" placeholder="可选说明" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-switch v-model="form.is_system" :disabled="!!form.id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ viewOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!viewOnly" type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight(280)

/** 系统参数分类（左侧固定项） */
const PRESET_CATEGORIES = [
  { category_code: '01', category_name: '资金来源' }
]

const list = ref<any[]>([])
const nameKeyword = ref('')
const nameFilter = ref('')
const loading = ref(false)
const visible = ref(false)
const viewOnly = ref(false)
const activeCategory = ref('')
const form = ref<any>(emptyForm())

function emptyForm() {
  return {
    category_code: '',
    category_name: '',
    item_code: '',
    item_name: '',
    value1: '',
    value2: '',
    value3: '',
    value4: '',
    value5: '',
    value6: '',
    is_system: false
  }
}

function isMetaRow(row: any) {
  const code = String(row.item_code || '')
  const key = String(row.config_key || '')
  return code === '__meta__' || key.endsWith('.__meta__')
}

const categories = computed(() => [...PRESET_CATEGORIES])

const filteredList = computed(() => {
  let rows = list.value.filter((r) => !isMetaRow(r))
  if (activeCategory.value) {
    rows = rows.filter((r) => String(r.category_code || '') === activeCategory.value)
  } else {
    rows = []
  }
  const kw = nameFilter.value.trim().toLowerCase()
  if (kw) {
    rows = rows.filter((r) =>
      [r.item_name, r.item_code, r.category_name, r.value1].some((v) => String(v || '').toLowerCase().includes(kw))
    )
  }
  return [...rows].sort((a, b) => String(a.item_code || '').localeCompare(String(b.item_code || ''), 'zh-CN', { numeric: true }))
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/system/sys_config/list', { params: { limit: 500 } })
    if (data.code === 0) {
      const rows = (data.data ?? []) as any[]
      // 清理非「资金来源」及历史测试数据
      const toRemove = rows.filter((r) => {
        if (isMetaRow(r)) return false
        const code = String(r.category_code || '').trim()
        return code !== '01'
      })
      for (const row of toRemove) {
        try {
          await http.delete(`/system/sys_config/${row.id}`)
        } catch {
          /* ignore */
        }
      }
      const { data: again } = toRemove.length
        ? await http.get('/system/sys_config/list', { params: { limit: 500 } })
        : { data }
      list.value = (again.code === 0 ? again.data : rows) ?? []
    }
  } finally {
    loading.value = false
  }
}

function selectCategory(code: string) {
  activeCategory.value = code
}

function applyFilter() {
  nameFilter.value = nameKeyword.value
}

function resetFilter() {
  nameKeyword.value = ''
  nameFilter.value = ''
}

function onAddClick() {
  if (!activeCategory.value) {
    ElMessage.warning('请先在左侧选中系统参数，再新增配置')
    return
  }
  openForm()
}

function openForm(row?: any, readonly = false) {
  viewOnly.value = readonly
  if (row) {
    form.value = { ...row }
  } else {
    const cat = categories.value.find((c) => c.category_code === activeCategory.value)
    if (!cat) {
      ElMessage.warning('请先在左侧选中系统参数，再新增配置')
      return
    }
    form.value = {
      ...emptyForm(),
      category_code: cat.category_code,
      category_name: cat.category_name
    }
  }
  visible.value = true
}

function buildPayload() {
  const categoryCode = String(form.value.category_code ?? '').trim()
  const categoryName = String(form.value.category_name ?? '').trim()
  const itemCode = String(form.value.item_code ?? '').trim()
  const itemName = String(form.value.item_name ?? '').trim()
  const value1 = String(form.value.value1 ?? '').trim()
  const configKey =
    String(form.value.config_key || '').trim() || `${categoryCode}.${itemCode}`
  return {
    ...form.value,
    category_code: categoryCode,
    category_name: categoryName,
    item_code: itemCode,
    item_name: itemName,
    config_key: configKey,
    config_value: value1 || itemName,
    config_type: form.value.config_type || 'string',
    description: form.value.description || itemName
  }
}

async function save() {
  const payload = buildPayload()
  if (!payload.category_code || !payload.category_name || !payload.item_code || !payload.item_name) {
    ElMessage.warning('请填写编号和名称')
    return
  }
  if (payload.item_code === '__meta__') {
    ElMessage.warning('编号不可使用保留值')
    return
  }
  try {
    const { data } = form.value.id
      ? await http.put(`/system/sys_config/${form.value.id}`, payload)
      : await http.post('/system/sys_config', payload)
    if (data.code !== 0) {
      ElMessage.error(data.message || '保存失败')
      return
    }
    ElMessage.success('保存成功')
    visible.value = false
    activeCategory.value = payload.category_code
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除配置「${row.item_name || row.item_code}」？删除后不可恢复。`, '删除配置', {
    type: 'warning'
  })
  try {
    const { data } = await http.delete(`/system/sys_config/${row.id}`)
    if (data.code !== 0) {
      ElMessage.error(data.message || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    await load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}
</script>

<style scoped>
.config-layout {
  display: flex;
  gap: 12px;
  min-height: 0;
  height: 100%;
}

.config-sider {
  width: 240px;
  flex-shrink: 0;
  border: 1px solid var(--meis-border-light, #ebeef5);
  border-radius: 6px;
  background: var(--meis-surface, #fff);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sider-title {
  padding: 10px 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--meis-border-light, #ebeef5);
  background: var(--meis-surface-muted, #f5f7fa);
}

.sider-scroll {
  flex: 1;
  min-height: 360px;
}

.sider-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--meis-border-light, #f0f2f5);
}

.sider-item:hover {
  background: #f5f9ff;
}

.sider-item.active {
  background: #ecf5ff;
  color: var(--el-color-primary);
}

.sider-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.config-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--meis-surface-muted, #f5f7fa);
  border: 1px solid var(--meis-border-light, #ebeef5);
  border-radius: 6px;
}

.toolbar-label {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.name-input {
  width: 220px;
}
</style>
