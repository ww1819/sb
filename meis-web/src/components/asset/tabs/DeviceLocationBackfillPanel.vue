<template>
  <div class="loc-backfill">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再补录位置历史"
      class="loc-backfill__alert"
    />
    <template v-else>
      <div class="loc-backfill__toolbar">
        <el-button type="primary" @click="openCreate">新增补录</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <span class="loc-backfill__hint">保存为待确认；确认后生效且不可改删。默认不改写当前台账位置。</span>
      </div>
      <el-table v-loading="loading" :data="rows" border stripe class="system-table">
        <el-table-column prop="campus_name" label="院区" min-width="100" show-overflow-tooltip />
        <el-table-column prop="building_name" label="楼宇" min-width="100" show-overflow-tooltip />
        <el-table-column prop="location_floor" label="楼层" width="80" show-overflow-tooltip />
        <el-table-column prop="room_number" label="房间" width="90" show-overflow-tooltip />
        <el-table-column prop="location_detail" label="位置明细" min-width="120" show-overflow-tooltip />
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
            <span v-else class="loc-backfill__locked">已生效</span>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无补录记录（含待确认）" :image-size="72" />
        </template>
      </el-table>
    </template>

    <AppModal v-model="formVisible" :title="form.id ? '编辑补录位置' : '新增补录位置'" size="md">
      <el-form label-width="110px">
        <el-form-item label="院区">
          <RefSelect v-model="form.campus_id" link-table="campus" placeholder="选择院区" />
        </el-form-item>
        <el-form-item label="楼宇">
          <RefSelect v-model="form.building_id" link-table="building" placeholder="选择楼宇" />
        </el-form-item>
        <el-form-item label="楼层">
          <el-input v-model="form.location_floor" clearable placeholder="楼层" />
        </el-form-item>
        <el-form-item label="房间">
          <el-input v-model="form.room_number" clearable placeholder="房间号" />
        </el-form-item>
        <el-form-item label="位置明细">
          <el-input v-model="form.location_detail" clearable placeholder="补充说明" />
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
  campus_id: '',
  building_id: '',
  location_floor: '',
  room_number: '',
  location_detail: '',
  effective_from: '',
  effective_to: '',
  remark: ''
})

async function load() {
  if (!props.deviceId) return
  loading.value = true
  try {
    const { data } = await http.get(`/asset/ownership-period/location/by-device/${props.deviceId}`, {
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
  form.campus_id = ''
  form.building_id = ''
  form.location_floor = ''
  form.room_number = ''
  form.location_detail = ''
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
  form.campus_id = row.campus_id ? String(row.campus_id) : ''
  form.building_id = row.building_id ? String(row.building_id) : ''
  form.location_floor = String(row.location_floor ?? '')
  form.room_number = String(row.room_number ?? '')
  form.location_detail = String(row.location_detail ?? '')
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
  if (!form.location_floor && !form.room_number && !form.location_detail && !form.building_id) {
    ElMessage.warning('请至少填写楼层、房间、位置明细或楼宇之一')
    return
  }
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      device_id: props.deviceId,
      campus_id: form.campus_id || null,
      building_id: form.building_id || null,
      location_floor: form.location_floor || null,
      room_number: form.room_number || null,
      location_detail: form.location_detail || null,
      effective_from: form.effective_from,
      effective_to: form.effective_to,
      remark: form.remark,
      change_reason: 'manual_backfill',
      source_mode: 'manual_backfill'
    }
    if (form.id) {
      await http.put(`/asset/ownership-period/location/${form.id}`, body)
    } else {
      await http.post('/asset/ownership-period/location/backfill', body)
    }
    ElMessage.success('已保存草稿')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onConfirm(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确认后位置历史生效且不可修改删除，是否继续？', '确认生效', {
    type: 'warning'
  })
  await http.post(`/asset/ownership-period/location/${row.id}/confirm`)
  ElMessage.success('已确认生效')
  await load()
}

async function onDelete(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确定删除该待确认草稿？', '删除', { type: 'warning' })
  await http.delete(`/asset/ownership-period/location/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
watch(() => props.deviceId, load)
</script>

<style scoped>
.loc-backfill__toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.loc-backfill__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.loc-backfill__alert {
  margin-bottom: 12px;
}
.loc-backfill__locked {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
