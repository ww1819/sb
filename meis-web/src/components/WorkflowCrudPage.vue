<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="mergedConfig" @detail="openDetail">
      <template #toolbar-extra>
        <slot name="toolbar-extra" :form="form" :save="save" :reload="reloadForm" />
        <el-button v-if="form?.id && mergedConfig.saveUrl" type="warning" @click="save">保存</el-button>
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
        <el-button v-if="mergedConfig.saveUrl" type="primary" @click="save">保存</el-button>
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

const mergedConfig = computed<PageConfig>(() => ({
  ...props.config,
  saveUrl: props.config.saveUrl ?? props.saveUrl
}))

const drawerTitle = computed(() => `${props.config.title} 详情`)

async function openDetail(row: Record<string, unknown>) {
  const url = props.loadUrl ?? mergedConfig.value.saveUrl
  if (url) {
    const loadUrl = props.loadUrl ?? `${url}/${row.id}`
    const { data } = await http.get(loadUrl)
    form.value = data.data ?? { ...row }
  } else {
    form.value = { ...row }
  }
  visible.value = true
}

async function reloadForm() {
  if (!form.value?.id) return
  const url = mergedConfig.value.saveUrl
  if (!url) return
  const loadUrl = props.loadUrl ?? `${url}/${form.value.id}`
  const { data } = await http.get(loadUrl)
  form.value = data.data ?? form.value
  crudRef.value?.load()
}

async function save() {
  if (!form.value) return
  const url = mergedConfig.value.saveUrl
  if (!url) return
  const { data } = await http.post(url, form.value)
  form.value = data.data ?? form.value
  visible.value = false
  crudRef.value?.load()
}

async function submitApproval() {
  if (!form.value?.id) return
  const url = mergedConfig.value.saveUrl
  if (!url) return
  await http.post(`${url}/${form.value.id}/submit`, { applicantId: auth.user?.userId })
  await reloadForm()
}

defineExpose({ openDetail, form, reloadForm })
</script>

