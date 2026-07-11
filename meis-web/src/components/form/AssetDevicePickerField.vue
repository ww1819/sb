<template>
  <div class="device-picker-field">
    <el-button type="primary" plain :disabled="disabled" @click="pickerVisible = true">
      {{ selectedLabel }}
    </el-button>
    <AssetDevicePicker v-model="pickerVisible" @confirm="onPicked" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AssetDevicePicker from './AssetDevicePicker.vue'

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
    device_id: device.id != null ? String(device.id) : null,
    device_code: device.device_code,
    device_name: device.device_name,
    specification: device.specification,
    model: device.model,
    serial_number: device.serial_number,
    manufacturer_name: device.manufacturer_name,
    dept_name: device.dept_name
  })
}
</script>

<style scoped>
.device-picker-field {
  width: 100%;
}
</style>
