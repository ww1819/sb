<template>
  <div class="metrology-param-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="计量检定类型" name="type">
        <CrudPage :config="typeConfig" delete-url="/metrology/type" />
      </el-tab-pane>
      <el-tab-pane label="计量参量类别" name="category">
        <CrudPage :config="categoryConfig" />
      </el-tab-pane>
      <el-tab-pane label="检定机构" name="org">
        <CrudPage :config="orgConfig" />
      </el-tab-pane>
      <el-tab-pane label="计量模板" name="template">
        <CrudPage :config="templateConfig" detail-mode @detail="openTemplate" />
      </el-tab-pane>
    </el-tabs>
    <AppModal v-model="templateVisible" title="计量模板详情" size="xl">
      <template v-if="templateForm">
        <GroupedFormFields table="metrology_template" :model="templateForm" />
        <FormSection title="检定内容项" class="items-section">
          <el-table :data="templateItems" border size="small">
            <el-table-column prop="item_name" label="项目名称" min-width="140">
              <template #default="{ row }"><el-input v-model="row.item_name" size="small" /></template>
            </el-table-column>
            <el-table-column prop="item_content" label="检定内容" min-width="160">
              <template #default="{ row }"><el-input v-model="row.item_content" size="small" type="textarea" :rows="2" /></template>
            </el-table-column>
            <el-table-column prop="standard_value" label="标准值" width="120">
              <template #default="{ row }"><el-input v-model="row.standard_value" size="small" /></template>
            </el-table-column>
            <el-table-column prop="tolerance_range" label="允差" width="100">
              <template #default="{ row }"><el-input v-model="row.tolerance_range" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ $index }"><el-button link type="danger" @click="removeItem($index)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <el-button class="add-item-btn" @click="addItem">添加内容项</el-button>
        </FormSection>
      </template>
      <template #footer>
        <el-button @click="templateVisible = false">关闭</el-button>
        <el-button type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import AppModal from '@/components/AppModal.vue'
import type { PageConfig } from '@/config/pageRegistry'

const activeTab = ref('type')
const typeConfig: PageConfig = {
  title: '计量检定类型',
  apiBase: '/metrology',
  table: 'metrology_type',
  saveUrl: '/metrology/type',
  listPageUrl: '/metrology/type/page',
  listFilters: [
    { key: 'classification_group', label: '分类维度', dictType: 'metrology_classification_group' }
  ]
}
const categoryConfig: PageConfig = { title: '计量参量类别', apiBase: '/metrology', table: 'metrology_category' }
const orgConfig: PageConfig = { title: '检定机构', apiBase: '/metrology', table: 'metrology_org' }
const templateConfig: PageConfig = { title: '计量模板', apiBase: '/metrology', table: 'metrology_template' }
const templateVisible = ref(false)
const templateForm = ref<Record<string, unknown> | null>(null)
const templateItems = ref<Record<string, unknown>[]>([])

async function openTemplate(row: Record<string, unknown>) {
  const { data } = await http.get(`/metrology/template/${row.id}`)
  templateForm.value = data.data ?? { ...row }
  templateItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  templateVisible.value = true
}

function addItem() {
  templateItems.value.push({ item_name: '', item_content: '', is_required: true, sort_order: templateItems.value.length })
}

function removeItem(index: number) {
  templateItems.value.splice(index, 1)
}

async function saveTemplate() {
  if (!templateForm.value) return
  await http.post('/metrology/template', { ...templateForm.value, items: templateItems.value })
  ElMessage.success('保存成功')
  templateVisible.value = false
}
</script>

<style scoped>
.items-section { margin-top: 16px; }
.add-item-btn { margin-top: 8px; }
</style>
