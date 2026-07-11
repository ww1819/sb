<template>
  <AppModal v-model="visible" title="维护待机电流上下限" size="md" @close="reset">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="待机电流上下限为设备台账属性，修改后将回写 medical_device，与标签无关。"
      class="tip"
    />
    <el-form v-if="form" label-width="160px" class="limits-form">
      <el-form-item label="关联设备">
        <span>{{ deviceLabel }}</span>
      </el-form-item>
      <el-form-item label="待机电流上限(mA)" required>
        <el-input-number v-model="form.standby_current_max_ma" :controls="true" style="width: 100%" />
      </el-form-item>
      <el-form-item label="待机电流下限(mA)" required>
        <el-input-number v-model="form.standby_current_min_ma" :controls="true" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存到设备台账</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'

const props = defineProps<{
  modelValue: boolean
  tagId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const saving = ref(false)
const meta = ref<Record<string, unknown>>({})
const form = reactive<{ standby_current_max_ma?: number; standby_current_min_ma?: number }>({})

const deviceLabel = computed(() => {
  const name = meta.value.device_name
  const code = meta.value.device_code
  if (name) return `${name}${code ? `（${code}）` : ''}`
  return '—'
})

function reset() {
  meta.value = {}
  form.standby_current_max_ma = undefined
  form.standby_current_min_ma = undefined
}

async function load() {
  if (!props.tagId) return
  loading.value = true
  try {
    const { data } = await http.get(`/power/tag/${props.tagId}`)
    const row = data.data ?? {}
    meta.value = row
    if (!row.device_id) {
      ElMessage.warning('该标签未关联设备，无法维护待机电流')
      visible.value = false
      return
    }
    form.standby_current_max_ma = row.standby_current_max_ma != null ? Number(row.standby_current_max_ma) : undefined
    form.standby_current_min_ma = row.standby_current_min_ma != null ? Number(row.standby_current_min_ma) : undefined
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!props.tagId) return
  if (form.standby_current_max_ma == null || form.standby_current_min_ma == null) {
    ElMessage.warning('请填写待机电流上下限')
    return
  }
  saving.value = true
  try {
    await http.put(`/power/tag/${props.tagId}/standby-limits`, {
      standby_current_max_ma: form.standby_current_max_ma,
      standby_current_min_ma: form.standby_current_min_ma
    })
    ElMessage.success('已回写设备台账')
    emit('saved')
    visible.value = false
  } finally {
    saving.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) void load()
    else reset()
  }
)
</script>

<style scoped>
.tip {
  margin-bottom: 16px;
}
.limits-form {
  margin-top: 8px;
}
</style>
