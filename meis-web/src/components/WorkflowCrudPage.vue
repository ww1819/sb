<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail">
      <template #toolbar-extra>
        <slot name="toolbar-extra" :form="form" :save="save" :reload="reloadForm" />
        <el-button v-if="form?.id && saveUrl" type="warning" @click="save">保存</el-button>
        <el-button v-if="form?.id && businessType" type="primary" @click="submitApproval">提交审批</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="drawerTitle" size="xl">
      <template v-if="form">
        <GroupedFormFields :table="config.table" :model="form" />
        <slot name="drawer-extra" :form="form" :reload="reloadForm" />
        <ApprovalPanel
          v-if="businessType && form.id"
          :business-type="businessType"
          :business-id="String(form.id)"
          @changed="reloadForm"
        />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="saveUrl" type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from './CrudPage.vue'
import GroupedFormFields from './form/GroupedFormFields.vue'
import ApprovalPanel from './ApprovalPanel.vue'
import AppModal from './AppModal.vue'
import type { PageConfig } from '@/config/pageRegistry'

const props = defineProps<{
  config: PageConfig
  saveUrl?: string
  businessType?: string
  loadUrl?: string
}>()

const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const form = ref<Record<string, unknown> | null>(null)

const drawerTitle = computed(() => `${props.config.title} 详情`)

async function openDetail(row: Record<string, unknown>) {
  if (props.loadUrl || props.saveUrl) {
    const url = props.loadUrl ?? `${props.saveUrl}/${row.id}`
    const { data } = await http.get(url)
    form.value = data.data ?? { ...row }
  } else {
    form.value = { ...row }
  }
  visible.value = true
}

async function reloadForm() {
  if (!form.value?.id) return
  const url = props.loadUrl ?? `${props.saveUrl}/${form.value.id}`
  const { data } = await http.get(url)
  form.value = data.data ?? form.value
  crudRef.value?.load()
}

async function save() {
  if (!form.value || !props.saveUrl) return
  const { data } = await http.post(props.saveUrl, form.value)
  form.value = data.data ?? form.value
  visible.value = false
  crudRef.value?.load()
}

async function submitApproval() {
  if (!form.value?.id || !props.saveUrl) return
  await http.post(`${props.saveUrl}/${form.value.id}/submit`, { applicantId: auth.user?.userId })
  await reloadForm()
}

defineExpose({ openDetail, form, reloadForm })
</script>
