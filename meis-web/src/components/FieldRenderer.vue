<template>
  <component :is="inputComponent" v-model="model" v-bind="attrs">
    <template v-if="field.type === 'select' && options.length">
      <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
    </template>
  </component>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { FieldSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'

const props = defineProps<{ field: FieldSchema; modelValue: unknown }>()
const emit = defineEmits<{ 'update:modelValue': [v: unknown] }>()
const { loadDict } = useDict()
const options = ref<{ label: string; value: string }[]>([])

const model = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const inputComponent = computed(() => {
  if (props.field.type === 'textarea') return 'el-input'
  if (props.field.type === 'select') return 'el-select'
  if (props.field.type === 'number') return 'el-input-number'
  if (props.field.type === 'date') return 'el-date-picker'
  return 'el-input'
})

const attrs = computed(() => {
  if (props.field.type === 'textarea') return { type: 'textarea', rows: 3 }
  if (props.field.type === 'date') return { type: 'date', valueFormat: 'YYYY-MM-DD' }
  if (props.field.type === 'number') return { style: 'width:100%' }
  return {}
})

onMounted(async () => {
  if (props.field.dictType) options.value = await loadDict(props.field.dictType)
})
</script>
