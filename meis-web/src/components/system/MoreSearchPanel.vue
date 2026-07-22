<template>
  <div class="more-search-bar">
    <span class="more-search-title">更多检索</span>
    <el-select
      v-model="selectedKeys"
      multiple
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="1"
      placeholder="请选择检索项"
      clearable
      class="more-search-select"
    >
      <el-option v-for="f in fields" :key="f.key" :label="f.label" :value="f.key" />
    </el-select>

    <div v-for="key in selectedKeys" :key="key" class="more-search-field">
      <span class="more-search-field-label">{{ fieldMeta(key)?.label }}</span>
      <MoreSearchRefSelect
        v-if="fieldMeta(key)?.linkTable"
        :model-value="modelValue[key] ?? ''"
        :link-table="fieldMeta(key)!.linkTable!"
        :multiple="!!fieldMeta(key)?.multiple"
        :placeholder="fieldMeta(key)?.placeholder ?? `请输入${fieldMeta(key)?.label ?? ''}`"
        @update:model-value="onFieldChange(key, $event)"
        @update:label="onLabelChange(key, $event)"
        @search="onRefSearch"
      />
      <el-input
        v-else
        :model-value="modelValue[key] ?? ''"
        :placeholder="fieldMeta(key)?.placeholder ?? `请输入${fieldMeta(key)?.label ?? ''}`"
        clearable
        class="more-search-field-input"
        @update:model-value="onFieldChange(key, $event)"
        @keyup.enter="$emit('search')"
        @clear="onFieldClear(key)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick } from 'vue'
import type { MoreSearchField } from '@/config/pageRegistry'
import MoreSearchRefSelect from './MoreSearchRefSelect.vue'

const props = defineProps<{
  fields: MoreSearchField[]
  modelValue: Record<string, string>
  labels: Record<string, string>
  selected: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>]
  'update:labels': [value: Record<string, string>]
  'update:selected': [value: string[]]
  search: []
}>()

const selectedKeys = computed({
  get: () => props.selected,
  set: (keys: string[]) => {
    const removed = props.selected.filter((k) => !keys.includes(k))
    if (removed.length) {
      const next = { ...props.modelValue }
      const nextLabels = { ...props.labels }
      for (const key of removed) {
        next[key] = ''
        nextLabels[key] = ''
      }
      emit('update:modelValue', next)
      emit('update:labels', nextLabels)
    }
    emit('update:selected', keys)
  }
})

function fieldMeta(key: string) {
  return props.fields.find((f) => f.key === key)
}

function onFieldChange(key: string, value: string) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function onLabelChange(key: string, label: string) {
  emit('update:labels', { ...props.labels, [key]: label })
}

async function onRefSearch() {
  await nextTick()
  emit('search')
}

function onFieldClear(key: string) {
  onFieldChange(key, '')
  onLabelChange(key, '')
  emit('search')
}
</script>

<style scoped>
.more-search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  flex: 1;
  min-width: 0;
}

.more-search-title {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.more-search-select {
  width: 200px;
}

.more-search-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.more-search-field-label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.more-search-field-input {
  width: 168px;
}
</style>
