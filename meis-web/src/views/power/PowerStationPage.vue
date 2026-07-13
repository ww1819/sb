<template>
  <div class="power-station-page">
    <CrudPage ref="crudRef" :config="pageConfig" detail-mode @add="openCreate" @detail="openEdit">
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="openTags(row)">关联标签</el-button>
        <el-button link type="primary" @click="openReadings(row)">监测记录</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="formVisible" :title="formTitle" size="lg">
      <template v-if="record">
        <el-form label-width="120px">
          <GroupedFormFields :table="pageConfig.table" :model="record" :fields="formFields" />
        </el-form>
      </template>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>

    <AppModal v-model="tagsVisible" :title="tagsTitle" size="lg">
      <el-table v-loading="tagsLoading" :data="tagRows" row-key="id" max-height="400">
        <el-table-column prop="tag_code" label="标签编码" min-width="120" />
        <el-table-column prop="tag_name" label="标签名称" min-width="140" />
        <el-table-column prop="device_code" label="设备编码" min-width="120" />
        <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="install_date" label="安装日期" width="120" />
        <el-table-column prop="is_active" label="启用" width="80">
          <template #default="{ row }">{{ row.is_active ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </AppModal>

    <PowerCurrentReadingDialog
      v-model="readingVisible"
      :title="readingTitle"
      :list-url="readingListUrl"
      show-station-code
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import PowerCurrentReadingDialog from '@/components/power/PowerCurrentReadingDialog.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'

const baseConfig = getPageConfig('/power/station')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const pageConfig = computed(() => ({ ...baseConfig, saveUrl: '/power/station' }))

const formVisible = ref(false)
const record = ref<Record<string, unknown> | null>(null)
const tagsVisible = ref(false)
const tagsLoading = ref(false)
const tagRows = ref<Record<string, unknown>[]>([])
const tagsTitle = ref('关联标签')
const readingVisible = ref(false)
const readingTitle = ref('监测记录')
const readingListUrl = ref('')

const formTitle = computed(() => (record.value?.id ? '编辑基站' : '新增基站'))
const formFields = computed(() => getSchema('power_base_station').filter((f) => !f.readonly))

function openCreate() {
  record.value = { is_active: true, protocol_type: 'mqtt', status: 'online' }
  formVisible.value = true
}

async function openEdit(row: Record<string, unknown>) {
  const { data } = await http.get(`/power/station/${row.id}`)
  record.value = data.data ?? { ...row }
  formVisible.value = true
}

async function openTags(row: Record<string, unknown>) {
  tagsTitle.value = `关联标签 - ${row.station_name ?? row.station_code ?? ''}`
  tagsVisible.value = true
  tagsLoading.value = true
  try {
    const { data } = await http.get(`/power/station/${row.id}/tags`)
    tagRows.value = data.data ?? []
  } finally {
    tagsLoading.value = false
  }
}

function openReadings(row: Record<string, unknown>) {
  readingTitle.value = `监测记录 - ${row.station_name ?? row.station_code ?? ''}`
  readingListUrl.value = `/power/station/${row.id}/readings/page`
  readingVisible.value = true
}

async function save() {
  if (!record.value) return
  const { data } = await http.post('/power/station', record.value)
  record.value = data.data
  formVisible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}
</script>
