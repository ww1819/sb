<template>
  <el-autocomplete
    :model-value="modelValue ?? ''"
    :fetch-suggestions="querySearch"
    :placeholder="placeholderText"
    :disabled="disabled || !warehouseId"
    :trigger-on-focus="!!warehouseId"
    clearable
    value-key="value"
    style="width: 100%"
    @update:model-value="onInput"
    @select="onSelect"
    @clear="onClear"
    @focus="onFocus"
  >
    <template #default="{ item }">
      <div class="stock-opt">
        <span class="stock-opt__code">{{ item.device_code }}</span>
        <span class="stock-opt__name">{{ item.device_name }}</span>
      </div>
    </template>
  </el-autocomplete>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

type StockOption = Record<string, unknown> & {
  value: string
  device_code?: string
  device_name?: string
}

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    warehouseId?: string | null
    mode?: 'code' | 'name'
    placeholder?: string
    disabled?: boolean
    excludeIds?: string[]
  }>(),
  { mode: 'code', disabled: false, excludeIds: () => [] }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [device: Record<string, unknown>]
  clear: []
}>()

const placeholderText = computed(() => {
  if (!props.warehouseId) return '请先选择仓库'
  return props.placeholder || (props.mode === 'name' ? '输入资产名称检索' : '输入资产编码检索')
})

function onInput(v: string) {
  emit('update:modelValue', v ?? '')
}

function onClear() {
  emit('update:modelValue', '')
  emit('clear')
}

function onFocus() {
  if (!props.warehouseId) {
    ElMessage.warning('请先选择仓库')
  }
}

async function querySearch(queryString: string, cb: (rows: StockOption[]) => void) {
  if (!props.warehouseId) {
    cb([])
    return
  }
  const q = (queryString ?? '').trim()
  try {
    const params: Record<string, string | number> = {
      page: 1,
      size: 30,
      warehouse_id: String(props.warehouseId)
    }
    if (q) {
      if (props.mode === 'name') params.device_name = q
      else params.keyword = q
    }
    const { data } = await http.get('/asset/device/page', { params })
    if (data.code !== 0 && data.code !== 200) {
      cb([])
      return
    }
    const ban = new Set((props.excludeIds ?? []).map(String).filter(Boolean))
    const rows = ((data.data?.records ?? []) as Record<string, unknown>[])
      .filter((r) => {
        const id = String(r.id ?? '')
        return id && !ban.has(id)
      })
      .map((r) => ({
        ...r,
        value: props.mode === 'name' ? String(r.device_name ?? '') : String(r.device_code ?? '')
      }))
    cb(rows)
  } catch {
    cb([])
  }
}

function onSelect(item: StockOption) {
  emit('select', item)
}
</script>

<style scoped>
.stock-opt {
  display: flex;
  gap: 10px;
  align-items: baseline;
  min-width: 0;
}
.stock-opt__code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--el-text-color-regular);
  flex: 0 0 auto;
}
.stock-opt__name {
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
