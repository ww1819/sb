<template>
  <div class="master-detail-page">
    <CrudPage :config="config" @detail="loadDetail">
      <template #toolbar-extra>
        <el-button v-if="selectedId" type="warning" @click="saveMaster">保存主从</el-button>
        <el-button v-if="selectedId && showApproval" type="primary" @click="submitApproval">提交审批</el-button>
      </template>
    </CrudPage>

    <el-drawer v-model="detailVisible" :title="config.title + ' 编辑'" size="60%">
      <el-form v-if="master" label-width="120px">
        <el-form-item v-for="f in masterFields" :key="f.prop" :label="f.label">
          <FieldRenderer v-model="master[f.prop]" :field="f" />
        </el-form-item>
      </el-form>
      <MasterDetailForm :items="items" @add-item="addItem">
        <template #detail-columns>
          <el-table-column prop="device_name" label="设备名称" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="estimated_price" label="单价" width="100" />
          <el-table-column prop="total_price" label="金额" width="100" />
        </template>
      </MasterDetailForm>
      <ApprovalPanel
        v-if="selectedId && businessType"
        :business-type="businessType"
        :business-id="selectedId"
        @changed="loadDetail(master!)"
      />
    </el-drawer>
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
import type { PageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

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
const masterFields = computed(() => getSchema(props.config.table))

async function loadDetail(row: Record<string, unknown>) {
  selectedId.value = String(row.id)
  const { data } = await http.get(`${props.saveUrl}/${row.id}`)
  master.value = data.data
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
  detailVisible.value = true
}

function addItem() {
  items.value.push({ device_name: '', quantity: 1, estimated_price: 0, total_price: 0 })
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
