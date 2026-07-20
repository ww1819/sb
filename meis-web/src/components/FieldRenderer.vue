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
    :value-key="field.linkValueKey"
    :hide-code="field.linkHideCode"
    :fallback-label="linkFallbackLabel"
    :placeholder="field.placeholder || '请选择' + field.label"
    :disabled="field.readonly"
    :exclude-values="linkExcludeValues"
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
  <ImageListField
    v-else-if="field.type === 'imageList'"
    v-model="imageListModel"
    :disabled="field.readonly"
    :max="field.maxCount ?? 3"
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
import ImageListField from '@/components/form/ImageListField.vue'
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

/** 自引用上级字段编辑时排除自身，避免选成自己的上级 */
const linkExcludeValues = computed(() => {
  if (!props.field.linkTable || props.field.prop !== 'parent_id') return []
  const id = props.model?.id
  return id != null && id !== '' ? [String(id)] : []
})

/** 详情带回的名称快照，供下拉在选项未加载/未命中时回显 */
const linkFallbackLabel = computed(() => {
  if (!props.field.linkTable || !props.model) return ''
  const prop = props.field.prop
  if (!prop.endsWith('_id')) return ''
  const nameKey = prop.slice(0, -3) + '_name'
  const name = props.model[nameKey]
  return name != null && String(name).trim() !== '' ? String(name).trim() : ''
})

function onPickerModel(v: Record<string, unknown>) {
  if (!props.model) return
  Object.assign(props.model, v)
}

const fileModel = computed({
  get: () => (props.modelValue == null ? '' : String(props.modelValue)),
  set: (v) => emit('update:modelValue', v)
})

const imageListModel = computed({
  get: () => {
    const v = props.modelValue
    if (Array.isArray(v)) return v.map(String)
    return [] as string[]
  },
  set: (v: string[]) => emit('update:modelValue', v)
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
  if (props.field.type === 'textarea') {
    return { ...base, type: 'textarea', rows: props.field.rows ?? 3, style: 'width:100%' }
  }
  if (props.field.type === 'date') return { ...base, type: 'date', valueFormat: 'YYYY-MM-DD', style: 'width:100%' }
  if (props.field.type === 'datetime') return { ...base, type: 'datetime', valueFormat: 'YYYY-MM-DD HH:mm:ss', style: 'width:100%' }
  if (props.field.type === 'number') {
    return { ...base, controls: false, style: 'width:100%' }
  }
  if (props.field.dictType) return { ...base, clearable: true, style: 'width:100%' }
  return { ...base, style: 'width:100%' }
})

onMounted(async () => {
  if (props.field.dictType) options.value = await loadDict(props.field.dictType)
})
</script>
