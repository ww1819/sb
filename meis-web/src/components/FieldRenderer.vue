<template>
  <RepairDevicePickerField
    v-if="field.widget === 'repairDevicePicker'"
    :model-value="props.model ?? {}"
    :disabled="field.readonly"
    @update:model-value="onPickerModel"
  />
  <AssetDevicePickerField
    v-else-if="field.widget === 'devicePicker'"
    :model-value="props.model ?? {}"
    :disabled="field.readonly"
    @update:model-value="onPickerModel"
  />
  <PowerStationPickerField
    v-else-if="field.widget === 'stationPicker'"
    :model-value="props.model ?? {}"
    :disabled="field.readonly"
    @update:model-value="onPickerModel"
  />
  <RefSelect
    v-else-if="field.linkTable"
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
  <component
    v-else
    :is="inputComponent"
    v-model="model"
    v-bind="attrs"
    :disabled="field.readonly && !useNativeReadonly"
    :readonly="field.readonly && useNativeReadonly"
  >
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
import AssetDevicePickerField from '@/components/form/AssetDevicePickerField.vue'
import PowerStationPickerField from '@/components/form/PowerStationPickerField.vue'

const props = defineProps<{ field: FieldSchema; modelValue: unknown; model?: Record<string, unknown> }>()
const emit = defineEmits<{ 'update:modelValue': [v: unknown] }>()
const { loadDict } = useDict()
const options = ref<{ label: string; value: string }[]>([])

const model = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

function onPickerModel(v: Record<string, unknown>) {
  if (!props.model) return
  Object.assign(props.model, v)
}

const fileModel = computed({
  get: () => (props.modelValue == null ? '' : String(props.modelValue)),
  set: (v) => emit('update:modelValue', v)
})

const useDictSelect = computed(() => !!props.field.dictType)

const useNativeReadonly = computed(() => {
  if (props.field.dictType || props.field.type === 'select') return false
  if (props.field.type === 'number' || props.field.type === 'date' || props.field.type === 'datetime') return false
  return true
})

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
  const base: Record<string, unknown> = {}
  if (props.field.placeholder) base.placeholder = props.field.placeholder
  if (props.field.type === 'textarea') return { ...base, type: 'textarea', rows: 3, style: 'width:100%' }
  if (props.field.type === 'date') return { ...base, type: 'date', valueFormat: 'YYYY-MM-DD', style: 'width:100%' }
  if (props.field.type === 'datetime') return { ...base, type: 'datetime', valueFormat: 'YYYY-MM-DD HH:mm:ss', style: 'width:100%' }
  if (props.field.type === 'number') return { ...base, style: 'width:100%' }
  if (props.field.dictType) return { ...base, clearable: true, style: 'width:100%' }
  return { ...base, style: 'width:100%' }
})

onMounted(async () => {
  if (props.field.dictType) options.value = await loadDict(props.field.dictType)
})
</script>
