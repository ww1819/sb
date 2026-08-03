<template>
  <div class="device-child-panel">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再维护培训授权"
    />
    <template v-else>
      <div class="device-child-panel__toolbar">
        <el-button v-if="!readonly" type="primary" @click="openCreate">新增授权</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border stripe class="system-table">
        <el-table-column prop="user_name" label="人员" min-width="100" show-overflow-tooltip />
        <el-table-column prop="cert_name" label="证书名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="cert_no" label="证书编号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="auth_scope" label="授权范围" width="110" />
        <el-table-column prop="trained_at" label="培训日期" width="120" />
        <el-table-column prop="expiry_date" label="有效期至" width="120" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="!readonly" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无培训授权" :image-size="72" />
        </template>
      </el-table>
    </template>

    <AppModal v-model="visible" :title="form.id ? '编辑培训授权' : '新增培训授权'" size="md">
      <el-form label-width="100px">
        <el-form-item label="人员" required>
          <RefSelect v-model="form.user_id" link-table="sys_user" placeholder="选择用户" />
        </el-form-item>
        <el-form-item label="证书名称">
          <el-input v-model="form.cert_name" />
        </el-form-item>
        <el-form-item label="证书编号">
          <el-input v-model="form.cert_no" />
        </el-form-item>
        <el-form-item label="授权范围">
          <el-select v-model="form.auth_scope" clearable style="width: 100%">
            <el-option label="操作" value="operate" />
            <el-option label="维护" value="maintain" />
            <el-option label="操作+维护" value="operate_maintain" />
          </el-select>
        </el-form-item>
        <el-form-item label="培训日期">
          <el-date-picker v-model="form.trained_at" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="form.expiry_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
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
import RefSelect from '@/components/form/RefSelect.vue'
import PageEmpty from '@/components/table/PageEmpty.vue'

const props = defineProps<{ deviceId?: string; readonly?: boolean }>()
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const form = reactive({
  id: '',
  user_id: '',
  cert_name: '',
  cert_no: '',
  auth_scope: 'operate',
  trained_at: '',
  expiry_date: '',
  remark: ''
})

async function load() {
  if (!props.deviceId) return
  loading.value = true
  try {
    const { data } = await http.get(`/asset/device-training-auth/by-device/${props.deviceId}`)
    rows.value = data.data ?? []
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(form, {
    id: '',
    user_id: '',
    cert_name: '',
    cert_no: '',
    auth_scope: 'operate',
    trained_at: '',
    expiry_date: '',
    remark: ''
  })
}

function openCreate() {
  reset()
  visible.value = true
}

function openEdit(row: Record<string, unknown>) {
  form.id = String(row.id ?? '')
  form.user_id = row.user_id ? String(row.user_id) : ''
  form.cert_name = String(row.cert_name ?? '')
  form.cert_no = String(row.cert_no ?? '')
  form.auth_scope = String(row.auth_scope ?? 'operate')
  form.trained_at = row.trained_at ? String(row.trained_at).slice(0, 10) : ''
  form.expiry_date = row.expiry_date ? String(row.expiry_date).slice(0, 10) : ''
  form.remark = String(row.remark ?? '')
  visible.value = true
}

async function save() {
  if (!props.deviceId || !form.user_id) {
    ElMessage.warning('请选择人员')
    return
  }
  saving.value = true
  try {
    const body = { ...form, device_id: props.deviceId }
    if (form.id) await http.put(`/asset/device-training-auth/${form.id}`, body)
    else await http.post('/asset/device-training-auth', body)
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确定删除该授权？', '删除', { type: 'warning' })
  await http.delete(`/asset/device-training-auth/${row.id}`)
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
</style>
