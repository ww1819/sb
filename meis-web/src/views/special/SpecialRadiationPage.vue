<template>
  <div class="special-radiation-page">
    <CrudPage ref="crudRef" :config="pageConfig" detail-mode @detail="openDetail" @add="openCreate">
      <template #toolbar-extra>
        <el-checkbox v-model="expiringOnly" @change="reload">仅显示30天内证照到期</el-checkbox>
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="openDetail(row)">编辑</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="lg">
      <template v-if="record">
        <GroupedFormFields :table="pageConfig.table" :model="record" :fields="formFields" />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const baseConfig = getPageConfig('/special/radiation')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const record = ref<Record<string, unknown> | null>(null)
const expiringOnly = ref(false)

const pageConfig = computed(() => ({
  ...baseConfig,
  listParams: { expiringOnly: expiringOnly.value || undefined }
}))

const modalTitle = computed(() => (record.value?.id ? '编辑特种设备' : '登记特种设备'))
const formFields = getSchema('special_device')

function openCreate() {
  record.value = { special_type: 'radiation', operator_cert_required: true }
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/special/radiation/${row.id}`)
  record.value = data.data
  visible.value = true
}

async function save() {
  if (!record.value) return
  const { data } = await http.post('/special/radiation', record.value)
  record.value = data.data
  visible.value = false
  ElMessage.success('保存成功')
  reload()
}

function reload() {
  crudRef.value?.load()
}
</script>
