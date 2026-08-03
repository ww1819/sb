<template>
  <div class="own-backfill">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再补录归属历史"
      class="own-backfill__alert"
    />
    <template v-else>
      <div class="own-backfill__toolbar">
        <el-button type="primary" @click="openCreate">新增补录</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <span class="own-backfill__hint">保存为待确认；确认后生效且不可改删。默认不改写当前台账归属。</span>
      </div>
      <el-table v-loading="loading" :data="rows" border stripe class="system-table">
        <el-table-column prop="campus_name" label="院区" min-width="100" show-overflow-tooltip />
        <el-table-column prop="owner_type" label="归属类型" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.owner_type" dict-type="owner_type" />
          </template>
        </el-table-column>
        <el-table-column label="仓库/科室" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.owner_type === 'warehouse' ? row.warehouse_name || '—' : row.dept_name || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="effective_from" label="开始" width="170">
          <template #default="{ row }">{{ formatDisplayDateTime(row.effective_from) }}</template>
        </el-table-column>
        <el-table-column prop="effective_to" label="结束" width="170">
          <template #default="{ row }">{{ formatDisplayDateTime(row.effective_to) }}</template>
        </el-table-column>
        <el-table-column prop="confirm_status" label="确认状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.confirm_status" dict-type="confirm_status" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.confirm_status === 'draft'">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="success" @click="onConfirm(row)">确认</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
            <span v-else class="own-backfill__locked">已生效</span>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无补录记录（含待确认）" :image-size="72" />
        </template>
      </el-table>
    </template>

    <AppModal v-model="formVisible" :title="form.id ? '编辑补录归属' : '新增补录归属'" size="md">
      <el-form label-width="110px">
        <el-form-item label="归属类型" required>
          <el-radio-group v-model="form.owner_type">
            <el-radio value="dept">科室</el-radio>
            <el-radio value="warehouse">仓库</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="院区">
          <RefSelect v-model="form.campus_id" link-table="campus" placeholder="选择院区" />
        </el-form-item>
        <el-form-item v-if="form.owner_type === 'dept'" label="科室" required>
          <RefSelect v-model="form.dept_id" link-table="department" placeholder="选择科室" />
        </el-form-item>
        <el-form-item v-else label="仓库" required>
          <RefSelect v-model="form.warehouse_id" link-table="warehouse" placeholder="选择仓库" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="form.effective_from"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="form.effective_to"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
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
import StatusTag from '@/components/table/StatusTag.vue'
import { formatDisplayDateTime } from '@/utils/datetime'

const props = defineProps<{
  deviceId?: string
}>()

const loading = ref(false)
const saving = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const formVisible = ref(false)
const form = reactive<Record<string, unknown>>({
  id: '',
  owner_type: 'dept',
  campus_id: '',
  dept_id: '',
  warehouse_id: '',
  effective_from: '',
  effective_to: '',
  remark: ''
})

async function load() {
  if (!props.deviceId) return
  loading.value = true
  try {
    const { data } = await http.get(`/asset/ownership-period/by-device/${props.deviceId}`, {
      params: { include_draft: true }
    })
    rows.value = (data.data ?? []).filter(
      (r: Record<string, unknown>) =>
        r.confirm_status === 'draft' || r.change_reason === 'manual_backfill' || r.source_mode === 'manual_backfill'
    )
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = ''
  form.owner_type = 'dept'
  form.campus_id = ''
  form.dept_id = ''
  form.warehouse_id = ''
  form.effective_from = ''
  form.effective_to = ''
  form.remark = ''
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(row: Record<string, unknown>) {
  form.id = String(row.id ?? '')
  form.owner_type = String(row.owner_type ?? 'dept')
  form.campus_id = row.campus_id ? String(row.campus_id) : ''
  form.dept_id = row.dept_id ? String(row.dept_id) : ''
  form.warehouse_id = row.warehouse_id ? String(row.warehouse_id) : ''
  form.effective_from = toPicker(row.effective_from)
  form.effective_to = toPicker(row.effective_to)
  form.remark = String(row.remark ?? '')
  formVisible.value = true
}

function toPicker(v: unknown) {
  if (!v) return ''
  const s = String(v)
  if (s.includes('T')) return s.slice(0, 19)
  return s.replace(' ', 'T').slice(0, 19)
}

async function save() {
  if (!props.deviceId) return
  if (!form.effective_from || !form.effective_to) {
    ElMessage.warning('请填写起止时间')
    return
  }
  if (form.owner_type === 'dept' && !form.dept_id) {
    ElMessage.warning('请选择科室')
    return
  }
  if (form.owner_type === 'warehouse' && !form.warehouse_id) {
    ElMessage.warning('请选择仓库')
    return
  }
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      device_id: props.deviceId,
      owner_type: form.owner_type,
      campus_id: form.campus_id || null,
      dept_id: form.owner_type === 'dept' ? form.dept_id : null,
      warehouse_id: form.owner_type === 'warehouse' ? form.warehouse_id : null,
      effective_from: form.effective_from,
      effective_to: form.effective_to,
      remark: form.remark,
      change_reason: 'manual_backfill',
      source_mode: 'manual_backfill'
    }
    if (form.id) {
      await http.put(`/asset/ownership-period/${form.id}`, body)
    } else {
      await http.post('/asset/ownership-period/backfill', body)
    }
    ElMessage.success('已保存草稿')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onConfirm(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确认后归属历史生效且不可修改删除，是否继续？', '确认生效', {
    type: 'warning'
  })
  await http.post(`/asset/ownership-period/${row.id}/confirm`)
  ElMessage.success('已确认生效')
  await load()
}

async function onDelete(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确定删除该待确认草稿？', '删除', { type: 'warning' })
  await http.delete(`/asset/ownership-period/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
watch(() => props.deviceId, load)
</script>

<style scoped>
.own-backfill__toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.own-backfill__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.own-backfill__alert {
  margin-bottom: 12px;
}
.own-backfill__locked {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
