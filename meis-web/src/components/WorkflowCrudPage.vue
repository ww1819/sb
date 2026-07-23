<template>
  <div class="workflow-crud">
    <CrudPage
      ref="crudRef"
      :config="mergedConfig"
      detail-mode
      :can-edit="canEdit"
      :can-delete="canDelete"
      :hide-operation-column="hideOperationColumn"
      @detail="openDetail"
      @add="openCreate"
    >
      <template #toolbar-extra>
        <slot name="list-toolbar-extra" />
      </template>
      <template #extra-columns>
        <slot name="extra-columns" />
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="drawerTitle" size="xl">
      <template #header-actions>
        <slot
          name="toolbar-extra"
          :form="form"
          :save="save"
          :reload="reloadForm"
          :editor-mode="editorMode"
        />
        <el-button v-if="form?.id && changeLogApi" @click="changeLogVisible = true">修改记录</el-button>
        <el-button v-if="form?.id && businessType && !headerReadonly" type="primary" @click="submitApproval">
          提交审批
        </el-button>
      </template>
      <template v-if="form">
        <el-form :disabled="headerReadonly" label-position="top">
          <GroupedFormFields :table="config.table" :model="form" />
        </el-form>
        <slot name="drawer-extra" :form="form" :reload="reloadForm" :editor-mode="editorMode" />
        <ApprovalPanel
          v-if="businessType && form.id && !headerReadonly"
          :business-type="businessType"
          :business-id="String(form.id)"
          @changed="reloadForm"
        />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="showSave" type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>

    <DocChangeHistoryDrawer v-model="changeLogVisible" :api-url="changeLogApi" />
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
import DocChangeHistoryDrawer from './DocChangeHistoryDrawer.vue'
import { calcItemNextDueDate } from '@/utils/cycleDays'
import type { PageConfig } from '@/config/pageRegistry'

const props = defineProps<{
  config: PageConfig
  saveUrl?: string
  businessType?: string
  loadUrl?: string
  /** 为 false 时不展示修改记录按钮（默认有 saveUrl 即展示） */
  enableDocChangeLog?: boolean
  canEdit?: (row: Record<string, unknown>) => boolean
  canDelete?: (row: Record<string, unknown>) => boolean
  hideOperationColumn?: boolean
}>()

const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const changeLogVisible = ref(false)
const form = ref<Record<string, unknown> | null>(null)
/** full=头表+明细；items=仅调明细（已审核入口） */
const editorMode = ref<'full' | 'items'>('full')

const mergedConfig = computed<PageConfig>(() => ({
  ...props.config,
  saveUrl: props.config.saveUrl ?? props.saveUrl
}))

const headerReadonly = computed(
  () => editorMode.value === 'items' || form.value?.approval_status === 'approved'
)

const showSave = computed(() => {
  if (!mergedConfig.value.saveUrl) return false
  if (editorMode.value === 'items') return true
  return form.value?.approval_status !== 'approved'
})

const drawerTitle = computed(() => {
  const base = props.config.title
  if (editorMode.value === 'items') return `${base} · 设备明细`
  if (!form.value?.id) return `新增${base}`
  return `${base} 详情`
})

const changeLogApi = computed(() => {
  if (props.enableDocChangeLog === false) return ''
  const base = mergedConfig.value.saveUrl
  const id = form.value?.id
  if (!base || !id) return ''
  return `${base}/${id}/change-logs`
})

function openCreate() {
  editorMode.value = 'full'
  form.value = {
    approval_status: 'draft',
    status: 'active',
    items: []
  }
  visible.value = true
}

function fillMissingItemNextDue(f: Record<string, unknown>) {
  const items = f.items
  if (!Array.isArray(items)) return
  for (const raw of items) {
    const item = raw as Record<string, unknown>
    if (item.next_due_date == null || item.next_due_date === '') {
      item.next_due_date = calcItemNextDueDate(f, item.last_done_date)
    }
  }
}

async function loadRow(row: Record<string, unknown>) {
  const url = props.loadUrl ?? mergedConfig.value.saveUrl
  if (url) {
    const loadUrl = props.loadUrl ?? `${url}/${row.id}`
    const { data } = await http.get(loadUrl)
    form.value = data.data ?? { ...row }
  } else {
    form.value = { ...row }
  }
  if (!Array.isArray(form.value.items)) form.value.items = []
  fillMissingItemNextDue(form.value)
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  editorMode.value = 'full'
  await loadRow(row)
}

async function openItemsOnly(row: Record<string, unknown>) {
  editorMode.value = 'items'
  await loadRow(row)
}

async function reloadForm() {
  if (!form.value?.id) return
  const url = mergedConfig.value.saveUrl
  if (!url) return
  const loadUrl = props.loadUrl ?? `${url}/${form.value.id}`
  const { data } = await http.get(loadUrl)
  form.value = data.data ?? form.value
  if (form.value && !Array.isArray(form.value.items)) form.value.items = []
  crudRef.value?.load()
}

async function save() {
  if (!form.value) return
  const url = mergedConfig.value.saveUrl
  if (!url) return
  const { data } = await http.post(url, form.value)
  form.value = data.data ?? form.value
  if (form.value && !Array.isArray(form.value.items)) form.value.items = []
  crudRef.value?.load()
}

async function submitApproval() {
  if (!form.value?.id) return
  const url = mergedConfig.value.saveUrl
  if (!url) return
  await http.post(`${url}/${form.value.id}/submit`, { applicantId: auth.user?.userId })
  await reloadForm()
}

function load() {
  crudRef.value?.load()
}

function remove(row: Record<string, unknown>) {
  return crudRef.value?.remove?.(row)
}

defineExpose({
  openDetail,
  openCreate,
  openItemsOnly,
  form,
  reloadForm,
  load,
  remove,
  editorMode
})
</script>
