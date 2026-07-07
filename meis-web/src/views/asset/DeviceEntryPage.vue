<template>
  <div class="entry-page">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail">
      <template #toolbar-extra>
        <el-button v-if="entry?.id && entry.status !== 'completed'" type="success" @click="completeEntry">完成入库生成台账</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" title="设备入库 详情" size="xl">
      <template v-if="entry">
        <GroupedFormFields :table="config.table" :model="entry" />
        <el-table :data="items" border class="mt-8" max-height="320">
          <el-table-column v-for="f in itemFields" :key="f.prop" :label="f.label" :min-width="120">
            <template #default="{ row }">
              <FieldRenderer v-model="row[f.prop]" :field="f" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="warning" @click="save">保存</el-button>
        <el-button v-if="entry?.status !== 'completed'" type="success" @click="completeEntry">完成入库生成台账</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getDetailFields } from '@/config/pageSchemas'

const config = getPageConfig('/asset/entry')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const entry = ref<Record<string, unknown> | null>(null)
const items = ref<Record<string, unknown>[]>([])
const itemFields = getDetailFields('device_entry_item')

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/asset/entry/${row.id}`)
  entry.value = data.data
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
  visible.value = true
}

async function save() {
  if (!entry.value) return
  await http.post('/asset/entry', { ...entry.value, items: items.value })
  visible.value = false
  crudRef.value?.load()
}

async function completeEntry() {
  if (!entry.value?.id) return
  await ElMessageBox.confirm('确认完成入库？将按明细数量生成设备台账。', '完成入库')
  const { data } = await http.post(`/asset/entry/${entry.value.id}/complete`, {})
  ElMessage.success(`已生成 ${data.data?.device_count ?? 0} 台设备台账`)
  visible.value = false
  crudRef.value?.load()
}
</script>

<style scoped>
.mt-8 { margin-top: 8px; }
</style>
