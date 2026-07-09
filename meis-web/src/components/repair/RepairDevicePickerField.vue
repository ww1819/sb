<template>
  <div class="device-picker-field">
    <el-button type="primary" plain :disabled="disabled" @click="pickerVisible = true">
      {{ selectedLabel }}
    </el-button>
    <RepairDevicePicker v-model="pickerVisible" @confirm="onPicked" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import RepairDevicePicker from './RepairDevicePicker.vue'

const props = defineProps<{
  modelValue: Record<string, unknown>
  disabled?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [v: Record<string, unknown>] }>()

const pickerVisible = ref(false)

const selectedLabel = computed(() => {
  const name = props.modelValue.device_name
  const code = props.modelValue.device_code
  if (name) return `${name}${code ? `（${code}）` : ''}`
  return '选择设备'
})

function onPicked(device: Record<string, unknown>) {
  emit('update:modelValue', {
    ...props.modelValue,
    device_id: device.id,
    device_code: device.device_code,
    device_name: device.device_name,
    report_dept_id: device.dept_id
  })
}
</script>

<style scoped>
.device-picker-field {
  width: 100%;
}
</style>
