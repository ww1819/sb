<template>
  <div class="device-child-panel">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再维护证照"
    />
    <template v-else>
      <div class="device-child-panel__toolbar">
        <el-button v-if="!readonly" type="primary" @click="openCreate">新增证照</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border stripe class="system-table">
        <el-table-column prop="license_type" label="证照类型" width="120">
          <template #default="{ row }">
            <StatusTag :value="row.license_type" dict-type="device_license_type" />
          </template>
        </el-table-column>
        <el-table-column prop="license_no" label="证号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="issue_date" label="发证日期" width="120" />
        <el-table-column prop="expiry_date" label="有效期至" width="120" />
        <el-table-column prop="issuer_name" label="发证机关" min-width="120" show-overflow-tooltip />
        <el-table-column prop="file_name" label="附件" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="!readonly" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无设备证照" :image-size="72" />
        </template>
      </el-table>
    </template>

    <AppModal v-model="visible" :title="form.id ? '编辑证照' : '新增证照'" size="md">
      <el-form label-width="100px">
        <el-form-item label="证照类型" required>
          <el-select v-model="form.license_type" style="width: 100%">
            <el-option v-for="o in licenseTypes" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="证号">
          <el-input v-model="form.license_no" />
        </el-form-item>
        <el-form-item label="发证日期">
          <el-date-picker v-model="form.issue_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="form.expiry_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发证机关">
          <el-input v-model="form.issuer_name" />
        </el-form-item>
        <el-form-item label="附件">
          <el-upload :show-file-list="false" :http-request="onUpload" :disabled="uploading">
            <el-button :loading="uploading">上传文件</el-button>
          </el-upload>
          <span v-if="form.file_name" class="device-child-panel__file">{{ form.file_name }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import PageEmpty from '@/components/table/PageEmpty.vue'
import StatusTag from '@/components/table/StatusTag.vue'
import { useDict } from '@/composables/useDict'

const props = defineProps<{ deviceId?: string; readonly?: boolean }>()
const { loadDict, getCached } = useDict()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const visible = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const licenseTypes = ref<{ label: string; value: string }[]>([])
const form = reactive({
  id: '',
  license_type: 'registration',
  license_no: '',
  issue_date: '',
  expiry_date: '',
  issuer_name: '',
  file_url: '',
  file_name: '',
  remark: ''
})

async function load() {
  if (!props.deviceId) return
  loading.value = true
  try {
    await loadDict('device_license_type')
    licenseTypes.value = getCached('device_license_type')
    const { data } = await http.get(`/asset/device-license/by-device/${props.deviceId}`)
    rows.value = data.data ?? []
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(form, {
    id: '',
    license_type: 'registration',
    license_no: '',
    issue_date: '',
    expiry_date: '',
    issuer_name: '',
    file_url: '',
    file_name: '',
    remark: ''
  })
}

function openCreate() {
  reset()
  visible.value = true
}

function openEdit(row: Record<string, unknown>) {
  form.id = String(row.id ?? '')
  form.license_type = String(row.license_type ?? 'registration')
  form.license_no = String(row.license_no ?? '')
  form.issue_date = row.issue_date ? String(row.issue_date).slice(0, 10) : ''
  form.expiry_date = row.expiry_date ? String(row.expiry_date).slice(0, 10) : ''
  form.issuer_name = String(row.issuer_name ?? '')
  form.file_url = String(row.file_url ?? '')
  form.file_name = String(row.file_name ?? '')
  form.remark = String(row.remark ?? '')
  visible.value = true
}

async function onUpload(options: { file: File }) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    const { data } = await http.post('/file/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code === 0 && data.data?.url) {
      form.file_url = String(data.data.url)
      form.file_name = options.file.name
      ElMessage.success('上传成功')
    } else {
      ElMessage.error(data.message || '上传失败')
    }
  } finally {
    uploading.value = false
  }
}

async function save() {
  if (!props.deviceId || !form.license_type) {
    ElMessage.warning('请选择证照类型')
    return
  }
  saving.value = true
  try {
    const body = { ...form, device_id: props.deviceId }
    if (form.id) await http.put(`/asset/device-license/${form.id}`, body)
    else await http.post('/asset/device-license', body)
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确定删除该证照？', '删除', { type: 'warning' })
  await http.delete(`/asset/device-license/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
watch(() => props.deviceId, load)
</script>

<style scoped>
.device-child-panel__toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.device-child-panel__file {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
