<template>
  <div class="shared-device-page">
    <CrudPage ref="crudRef" :config="config" detail-mode @detail="openDetail" @add="openCreate">
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="openDetail(row)">编辑</el-button>
      </template>
    </CrudPage>
    <AppModal v-model="visible" :title="modalTitle" size="lg">
      <GroupedFormFields v-if="record" :table="config.table" :model="record" :fields="formFields" />
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

const config = getPageConfig('/shared/device')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const record = ref<Record<string, unknown> | null>(null)
const modalTitle = computed(() => (record.value?.id ? '编辑公用设备' : '登记公用设备'))
const formFields = getSchema('shared_device')

function openCreate() {
  record.value = { availability_status: 'available', is_active: true, fee_standard: 0 }
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/shared/device/${row.id}`)
  record.value = data.data
  visible.value = true
}

async function save() {
  if (!record.value) return
  const { data } = await http.post('/shared/device', record.value)
  record.value = data.data
  visible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}
</script>
