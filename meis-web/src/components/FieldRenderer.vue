<template>
  <RepairDevicePickerField
    v-if="field.widget === 'repairDevicePicker'"
    :model-value="model ?? {}"
    :disabled="field.readonly"
    @update:model-value="onRepairDeviceModel"
  />
  <RefSelect
    v-if="field.linkTable"
    v-model="model"
    :link-table="field.linkTable"
    :placeholder="'请选择' + field.label"
    :disabled="field.readonly"
  />
  <el-switch
    v-else-if="field.type === 'boolean'"
    v-model="model"
    :disabled="field.readonly"
  />
  <FileUploadField
    v-else-if="field.type === 'file'"
    v-model="fileModel"
    :placeholder="field.label"
    :disabled="field.readonly"
  />
  <component v-else :is="inputComponent" v-model="model" v-bind="attrs" :disabled="field.readonly">
    <template v-if="useDictSelect && options.length">
      <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
    </template>
  </component>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'
import RefSelect from '@/components/form/RefSelect.vue'
import FileUploadField from '@/components/form/FileUploadField.vue'
import RepairDevicePickerField from '@/components/repair/RepairDevicePickerField.vue'

const props = defineProps<{ field: FieldSchema; modelValue: unknown; model?: Record<string, unknown> }>()
const emit = defineEmits<{ 'update:modelValue': [v: unknown] }>()
const { loadDict } = useDict()
const options = ref<{ label: string; value: string }[]>([])

const model = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

function onRepairDeviceModel(v: Record<string, unknown>) {
  if (!props.model) return
  Object.assign(props.model, v)
}

const fileModel = computed({
  get: () => (props.modelValue == null ? '' : String(props.modelValue)),
  set: (v) => emit('update:modelValue', v)
})

const useDictSelect = computed(() => !!props.field.dictType)

const inputComponent = computed(() => {
  if (props.field.dictType) return 'el-select'
  if (props.field.type === 'textarea') return 'el-input'
  if (props.field.type === 'select') return 'el-select'
  if (props.field.type === 'number') return 'el-input-number'
  if (props.field.type === 'date') return 'el-date-picker'
  if (props.field.type === 'datetime') return 'el-date-picker'
  return 'el-input'
})

const attrs = computed(() => {
  if (props.field.type === 'textarea') return { type: 'textarea', rows: 3, style: 'width:100%' }
  if (props.field.type === 'date') return { type: 'date', valueFormat: 'YYYY-MM-DD', style: 'width:100%' }
  if (props.field.type === 'datetime') return { type: 'datetime', valueFormat: 'YYYY-MM-DD HH:mm:ss', style: 'width:100%' }
  if (props.field.type === 'number') return { style: 'width:100%' }
  if (props.field.dictType) return { clearable: true, style: 'width:100%' }
  return { style: 'width:100%' }
})

onMounted(async () => {
  if (props.field.dictType) options.value = await loadDict(props.field.dictType)
})
</script>
