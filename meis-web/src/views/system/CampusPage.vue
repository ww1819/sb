<template>
  <div v-loading="loading" class="meis-report-page">
    <div class="query-box">
      <div class="section-bar">查询条件</div>
      <div class="query-body">
        <el-form class="filter-form" label-width="100px" @submit.prevent>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="关键词">
                <el-input
                  v-model="keyword"
                  clearable
                  placeholder="编码 / 名称 / 地址 / 电话"
                  @clear="applyFilter"
                  @keyup.enter="applyFilter"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
              <el-form-item label-width="0">
                <el-button type="primary" @click="applyFilter">查询</el-button>
                <el-button @click="resetFilter">重置</el-button>
                <el-button type="primary" @click="openForm()">新增院区</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </div>

    <div class="table-wrap">
      <div class="section-bar">院区管理</div>
      <el-table
        :data="pagedList"
        border
        stripe
        height="100%"
        class="detail-table"
        empty-text="暂无数据"
      >
        <el-table-column label="序号" width="64" align="center">
          <template #default="{ $index }">{{ indexMethod($index) }}</template>
        </el-table-column>
        <el-table-column prop="campus_code" label="院区编码" width="100" />
        <el-table-column prop="campus_name" label="院区名称" min-width="120" />
        <el-table-column prop="address" label="地址" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contact_phone" label="联系电话" width="130" />
        <el-table-column prop="is_active" label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.is_active ? 'success' : 'info'" size="small">
              {{ row.is_active ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openChangeLog(row)">变更记录</el-button>
              <el-button link type="primary" @click="openForm(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <div class="table-footer-summary">
          <span>合计：</span>
          <span>总数量：{{ filteredList.length }}</span>
          <span>当前页面数量：{{ pagedList.length }}</span>
        </div>
        <div class="table-footer-pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 30, 50]"
            :total="filteredList.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </div>
    </div>

    <AppModal
      v-model="visible"
      :title="form.id ? '编辑院区' : '新增院区'"
      size="sm"
      placement="right"
      variant="report"
    >
      <el-form :model="form" class="filter-form" label-width="100px">
        <el-form-item label="院区编码" required>
          <el-input v-model="form.campus_code" maxlength="1" placeholder="A-Z 单字母" />
        </el-form-item>
        <el-form-item label="院区名称" required>
          <el-input v-model="form.campus_name" placeholder="院区名称" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="地址" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contact_phone" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.is_active" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>
    <EntityChangeHistoryDrawer v-model="changeLogVisible" entity-type="campus" :entity-id="changeLogId" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'

const list = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const visible = ref(false)
const changeLogVisible = ref(false)
const changeLogId = ref('')
const form = ref<any>({ is_active: true })
const page = ref(1)
const pageSize = ref(20)

function openChangeLog(row: any) {
  changeLogId.value = String(row.id)
  changeLogVisible.value = true
}

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((r) =>
    [r.campus_code, r.campus_name, r.address, r.contact_phone].some((v) =>
      String(v || '').toLowerCase().includes(kw)
    )
  )
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

function indexMethod(index: number) {
  return (page.value - 1) * pageSize.value + index + 1
}

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

function applyFilter() {
  page.value = 1
}

function resetFilter() {
  keyword.value = ''
  page.value = 1
}

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

<style scoped>
.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
