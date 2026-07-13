<template>
  <div class="shared-return-page">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @add="openCreate">
      <template #toolbar-extra>
        <el-button type="primary" @click="openCreate">申请归还</el-button>
      </template>
    </CrudPage>
    <AppModal v-model="visible" title="归还申请" size="lg">
      <GroupedFormFields v-if="form" table="shared_device_return" :model="form" :fields="formFields" />
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">提交</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const auth = useAuthStore()
const config = getPageConfig('/shared/return')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const form = ref<Record<string, unknown> | null>(null)
const formFields = getSchema('shared_device_return').filter((f) => !f.readonly)

function openCreate() {
  form.value = { applicant_id: auth.user?.id, return_date: new Date().toISOString().slice(0, 10) }
  visible.value = true
}

async function save() {
  if (!form.value) return
  await http.post('/shared/return', form.value)
  ElMessage.success('归还申请已提交')
  visible.value = false
  crudRef.value?.load()
}
</script>
