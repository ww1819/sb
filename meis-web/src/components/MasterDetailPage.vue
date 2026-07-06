<template>
  <div class="master-detail-page">
    <CrudPage :config="config" @detail="loadDetail">
      <template #toolbar-extra>
        <el-button v-if="selectedId" type="warning" @click="saveMaster">保存主从</el-button>
        <el-button v-if="selectedId && showApproval" type="primary" @click="submitApproval">提交审批</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="detailVisible" :title="config.title + ' 编辑'" size="xl">
      <template v-if="master">
        <GroupedFormFields :table="config.table" :model="master" />
        <MasterDetailForm :items="items" @add-item="addItem">
          <template #detail-columns>
            <el-table-column
              v-for="f in detailFields"
              :key="f.prop"
              :prop="f.prop"
              :label="f.label"
              :width="f.width"
              :min-width="f.width ? undefined : 120"
            >
              <template #default="{ row }">
                <FieldRenderer v-if="f.linkTable || f.dictType || f.type === 'boolean'" v-model="row[f.prop]" :field="f" />
                <el-input v-else-if="f.type === 'textarea'" v-model="row[f.prop]" type="textarea" :rows="2" />
                <el-input-number v-else-if="f.type === 'number'" v-model="row[f.prop]" :min="0" style="width:100%" />
                <el-input v-else v-model="row[f.prop]" />
              </template>
            </el-table-column>
          </template>
        </MasterDetailForm>
        <ApprovalPanel
          v-if="selectedId && businessType"
          :business-type="businessType"
          :business-id="selectedId"
          @changed="loadDetail(master!)"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMaster">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from './CrudPage.vue'
import MasterDetailForm from './MasterDetailForm.vue'
import FieldRenderer from './FieldRenderer.vue'
import ApprovalPanel from './ApprovalPanel.vue'
import AppModal from './AppModal.vue'
import GroupedFormFields from './form/GroupedFormFields.vue'
import type { PageConfig } from '@/config/pageRegistry'
import { getDetailFields } from '@/config/pageSchemas'

const props = defineProps<{
  config: PageConfig
  saveUrl: string
  businessType?: string
}>()

const auth = useAuthStore()
const detailVisible = ref(false)
const master = ref<Record<string, unknown> | null>(null)
const items = ref<Record<string, unknown>[]>([])
const selectedId = ref('')
const showApproval = computed(() => !!props.businessType)
const detailFields = computed(() => getDetailFields(props.config.detailTable ?? `${props.config.table}_item`))

function defaultItem() {
  const item: Record<string, unknown> = {}
  for (const f of detailFields.value) {
    item[f.prop] = f.type === 'number' ? 0 : f.type === 'boolean' ? false : ''
  }
  return item
}

async function loadDetail(row: Record<string, unknown>) {
  selectedId.value = String(row.id)
  const { data } = await http.get(`${props.saveUrl}/${row.id}`)
  master.value = data.data
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
  detailVisible.value = true
}

function addItem() {
  items.value.push(defaultItem())
}

async function saveMaster() {
  if (!master.value) return
  await http.post(props.saveUrl, { ...master.value, items: items.value })
  detailVisible.value = false
}

async function submitApproval() {
  await http.post(`${props.saveUrl}/${selectedId.value}/submit`, { applicantId: auth.user?.userId })
}
</script>
