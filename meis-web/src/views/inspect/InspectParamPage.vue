<template>
  <div class="inspect-param-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="巡检类型" name="type">
        <CrudPage ref="typeRef" :config="typeConfig" />
      </el-tab-pane>
      <el-tab-pane label="巡检模板" name="template">
        <CrudPage
          ref="templateRef"
          :config="templateConfig"
          detail-mode
          @add="openNewTemplate"
          @detail="openTemplate"
        />
      </el-tab-pane>
    </el-tabs>

    <AppModal v-model="templateVisible" :title="templateModalTitle" size="xl">
      <template v-if="templateForm">
        <GroupedFormFields table="inspection_template" :model="templateForm" />
        <FormSection title="巡检内容项" class="items-section">
          <el-table :data="templateItems" border size="small">
            <el-table-column prop="item_code" label="编码" width="100">
              <template #default="{ row }"><el-input v-model="row.item_code" size="small" /></template>
            </el-table-column>
            <el-table-column prop="item_name" label="项目名称" min-width="140">
              <template #default="{ row }"><el-input v-model="row.item_name" size="small" /></template>
            </el-table-column>
            <el-table-column prop="item_content" label="巡检内容" min-width="180">
              <template #default="{ row }"><el-input v-model="row.item_content" size="small" type="textarea" :rows="2" /></template>
            </el-table-column>
            <el-table-column prop="standard_value" label="标准值" width="120">
              <template #default="{ row }"><el-input v-model="row.standard_value" size="small" /></template>
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
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import AppModal from '@/components/AppModal.vue'
import type { PageConfig } from '@/config/pageRegistry'

const activeTab = ref('type')
const typeRef = ref<InstanceType<typeof CrudPage> | null>(null)
const templateRef = ref<InstanceType<typeof CrudPage> | null>(null)
const typeConfig: PageConfig = { title: '巡检类型', apiBase: '/inspect', table: 'inspection_type' }
const templateConfig: PageConfig = { title: '巡检模板', apiBase: '/inspect', table: 'inspection_template' }
const templateVisible = ref(false)
const templateForm = ref<Record<string, unknown> | null>(null)
const templateItems = ref<Record<string, unknown>[]>([])
const templateModalTitle = computed(() => (templateForm.value?.id ? '编辑巡检模板' : '新增巡检模板'))

function openNewTemplate() {
  templateForm.value = { is_active: true, template_code: '', template_name: '' }
  templateItems.value = []
  templateVisible.value = true
}

async function openTemplate(row: Record<string, unknown>) {
  const { data } = await http.get(`/inspect/template/${row.id}`)
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
  if (!String(templateForm.value.template_name ?? '').trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  const payload = { ...templateForm.value, items: templateItems.value }
  if (!payload.id) delete payload.id
  await http.post('/inspect/template', payload)
  ElMessage.success('保存成功')
  templateVisible.value = false
  templateRef.value?.load()
}
</script>

<style scoped>
.items-section { margin-top: 16px; }
.add-item-btn { margin-top: 8px; }
</style>
