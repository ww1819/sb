<template>
  <div class="station-picker-field">
    <el-button type="primary" plain :disabled="disabled" @click="pickerVisible = true">
      {{ selectedLabel }}
    </el-button>
    <PowerStationPicker v-model="pickerVisible" @confirm="onPicked" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PowerStationPicker from './PowerStationPicker.vue'

const props = defineProps<{
  modelValue: Record<string, unknown>
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [v: Record<string, unknown>] }>()

const pickerVisible = ref(false)

const selectedLabel = computed(() => {
  const name = props.modelValue.station_name
  const code = props.modelValue.station_code
  if (name) return `${name}${code ? `（${code}）` : ''}`
  return '选择基站'
})

function onPicked(station: Record<string, unknown>) {
  emit('update:modelValue', {
    ...props.modelValue,
    station_id: station.id,
    station_code: station.station_code,
    station_name: station.station_name
  })
}
</script>

<style scoped>
.station-picker-field {
  width: 100%;
}
</style>
