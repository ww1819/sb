<template>
  <div class="part-replace-panel">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再登记非维修配件更换"
      class="part-replace-panel__alert"
    />
    <template v-else>
      <div class="part-replace-panel__toolbar">
        <el-button type="primary" @click="openCreate">新增更换</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <span class="part-replace-panel__hint">保存为待确认；确认后进入「配件更换记录」且不可改删。</span>
      </div>
      <el-table v-loading="loading" :data="rows" border stripe class="system-table">
        <el-table-column prop="replaced_at" label="更换时间" width="170">
          <template #default="{ row }">{{ formatDisplayDateTime(row.replaced_at) }}</template>
        </el-table-column>
        <el-table-column prop="part_code" label="配件编码" min-width="110" show-overflow-tooltip />
        <el-table-column prop="part_name" label="配件名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="unit_price" label="单价" width="90" />
        <el-table-column prop="total_price" label="金额" width="90" />
        <el-table-column prop="supplier_name" label="供应商" min-width="120" show-overflow-tooltip />
        <el-table-column prop="confirm_status" label="确认状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.confirm_status" dict-type="confirm_status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.confirm_status === 'draft'">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="success" @click="onConfirm(row)">确认</el-button>
              <el-button link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
            <span v-else class="part-replace-panel__locked">已生效</span>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无非维修配件更换" :image-size="72" />
        </template>
      </el-table>
    </template>

    <AppModal v-model="formVisible" :title="form.id ? '编辑配件更换' : '新增配件更换'" size="md">
      <el-form label-width="110px">
        <el-form-item label="配件" required>
          <RefSelect v-model="form.spare_part_id" link-table="spare_part" placeholder="选择配件档案" />
        </el-form-item>
        <el-form-item label="更换时间" required>
          <el-date-picker
            v-model="form.replaced_at"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="form.quantity" :min="0.001" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="form.unit_price" :min="0" :controls="true" style="width: 100%" />
        </el-form-item>
        <el-form-item label="供应商">
          <RefSelect v-model="form.supplier_id" link-table="supplier" placeholder="可选" />
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
const form = reactive<{
  id: string
  spare_part_id: string
  replaced_at: string
  quantity: number | undefined
  unit_price: number | undefined
  supplier_id: string
  remark: string
}>({
  id: '',
  spare_part_id: '',
  replaced_at: '',
  quantity: 1,
  unit_price: undefined,
  supplier_id: '',
  remark: ''
})

async function load() {
  if (!props.deviceId) return
  loading.value = true
  try {
    const { data } = await http.get(`/asset/part-replacement/by-device/${props.deviceId}`, {
      params: { include_draft: true }
    })
    rows.value = data.data ?? []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = ''
  form.spare_part_id = ''
  form.replaced_at = ''
  form.quantity = 1
  form.unit_price = undefined
  form.supplier_id = ''
  form.remark = ''
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(row: Record<string, unknown>) {
  form.id = String(row.id ?? '')
  form.spare_part_id = row.spare_part_id ? String(row.spare_part_id) : ''
  form.replaced_at = toPicker(row.replaced_at)
  form.quantity = row.quantity != null ? Number(row.quantity) : 1
  form.unit_price = row.unit_price != null ? Number(row.unit_price) : undefined
  form.supplier_id = row.supplier_id ? String(row.supplier_id) : ''
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
  if (!form.spare_part_id || !form.replaced_at || form.quantity == null) {
    ElMessage.warning('请填写配件、更换时间与数量')
    return
  }
  saving.value = true
  try {
    const body = {
      device_id: props.deviceId,
      spare_part_id: form.spare_part_id,
      replaced_at: form.replaced_at,
      quantity: form.quantity,
      unit_price: form.unit_price,
      supplier_id: form.supplier_id || null,
      remark: form.remark
    }
    if (form.id) {
      await http.put(`/asset/part-replacement/${form.id}`, body)
    } else {
      await http.post('/asset/part-replacement', body)
    }
    ElMessage.success('已保存草稿')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onConfirm(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确认后记录生效且不可修改删除，是否继续？', '确认生效', {
    type: 'warning'
  })
  await http.post(`/asset/part-replacement/${row.id}/confirm`)
  ElMessage.success('已确认生效')
  await load()
}

async function onDelete(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确定删除该待确认草稿？', '删除', { type: 'warning' })
  await http.delete(`/asset/part-replacement/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
watch(() => props.deviceId, load)
</script>

<style scoped>
.part-replace-panel__toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.part-replace-panel__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.part-replace-panel__alert {
  margin-bottom: 12px;
}
.part-replace-panel__locked {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
