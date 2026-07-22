<template>
  <el-select
    v-if="filter.dictType"
    :model-value="modelValue"
    :placeholder="filter.label"
    :multiple="filter.multiple"
    collapse-tags
    collapse-tags-tooltip
    clearable
    class="filter-item"
    @update:model-value="onChange"
  >
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
  <div v-else-if="filter.linkTable" class="filter-item filter-ref">
    <RefSelect
      :model-value="modelValue"
      :link-table="filter.linkTable"
      :placeholder="filter.label"
      :multiple="!!filter.multiple"
      @update:model-value="onChange"
    />
  </div>
  <el-date-picker
    v-else-if="filter.type === 'date'"
    :model-value="modelValue"
    type="date"
    :placeholder="filter.label"
    value-format="YYYY-MM-DD"
    clearable
    class="filter-item filter-date"
    @update:model-value="onChange"
  />
  <el-date-picker
    v-else-if="filter.type === 'daterange'"
    :model-value="modelValue"
    type="daterange"
    :range-separator="rangeSeparator"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    value-format="YYYY-MM-DD"
    class="filter-item filter-daterange"
    @update:model-value="onChange"
  />
  <el-select
    v-else-if="filter.options?.length"
    :model-value="modelValue"
    :placeholder="filter.label"
    :multiple="filter.multiple"
    collapse-tags
    collapse-tags-tooltip
    clearable
    class="filter-item"
    @update:model-value="onChange"
  >
    <el-option v-for="o in filter.options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
  <el-input-number
    v-else-if="filter.type === 'number'"
    :model-value="modelValue"
    :placeholder="filter.label"
    controls-position="right"
    class="filter-item filter-number"
    @update:model-value="onChange"
  />
</template>

<script setup lang="ts">
import RefSelect from '@/components/form/RefSelect.vue'
import type { ListFilter } from '@/config/pageRegistry'

const props = withDefaults(
  defineProps<{
    filter: ListFilter
    modelValue: unknown
    options?: { label: string; value: string }[]
    rangeSeparator?: string
    startPlaceholder?: string
    endPlaceholder?: string
  }>(),
  {
    options: () => [],
    rangeSeparator: '至',
    startPlaceholder: '开始',
    endPlaceholder: '结束'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
  change: []
}>()

function onChange(value: unknown) {
  emit('update:modelValue', value)
  emit('change')
}
</script>
