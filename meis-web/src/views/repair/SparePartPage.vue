<template>
  <CrudPage ref="crudRef" :config="config" :operation-column-width="220">
    <template #row-actions="{ row }">
      <el-button v-permission="'add'" link type="primary" @click.stop="onCopy(row)">复制</el-button>
    </template>
  </CrudPage>

  <FormDrawer v-model="copyVisible" title="复制配件（请重填编码）" size="lg" @save="saveCopy">
    <el-form label-width="120px">
      <GroupedFormFields table="spare_part" :model="copyForm" :fields="formFields" />
    </el-form>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import FormDrawer from '@/components/FormDrawer.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { pageRegistry } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const config: PageConfig = pageRegistry['/repair/spare-archive']
  ?? { title: '配件档案管理', apiBase: '/repair', table: 'spare_part', pinyinCode: true }

const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const copyVisible = ref(false)
const copyForm = reactive<Record<string, unknown>>({})
const formFields = computed(() =>
  getSchema('spare_part').map((f) =>
    f.prop === 'pinyin_code' ? { ...f, readonly: true } : f
  )
)

const STRIP_ON_COPY = new Set([
  'id',
  'part_code',
  'created_at',
  'updated_at',
  'created_by',
  'updated_by',
  'deleted_at',
  'deleted_by',
  'is_deleted'
])

async function onCopy(row: Record<string, unknown>) {
  let src = row
  if (row.id) {
    try {
      const { data } = await http.get(`/repair/spare_part/${row.id}`)
      if (data.data) src = data.data
    } catch {
      /* 详情失败则用列表行 */
    }
  }
  Object.keys(copyForm).forEach((k) => delete copyForm[k])
  for (const [k, v] of Object.entries(src)) {
    if (STRIP_ON_COPY.has(k)) continue
    copyForm[k] = v
  }
  copyForm.part_code = ''
  copyVisible.value = true
  ElMessage.info('已带出档案字段，请重新填写配件编码后保存')
}

async function saveCopy() {
  if (!String(copyForm.part_code ?? '').trim()) {
    ElMessage.warning('请填写配件编码')
    return
  }
  if (!String(copyForm.part_name ?? '').trim()) {
    ElMessage.warning('请填写配件名称')
    return
  }
  const payload: Record<string, unknown> = { ...copyForm }
  for (const [k, v] of Object.entries(payload)) {
    if ((k === 'id' || k.endsWith('_id') || k.endsWith('_by')) && (v === undefined || v === '')) {
      payload[k] = null
    }
  }
  delete payload.id
  const { data } = await http.post('/repair/spare_part', payload)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }
  copyVisible.value = false
  ElMessage.success('复制成功')
  crudRef.value?.load()
}
</script>
