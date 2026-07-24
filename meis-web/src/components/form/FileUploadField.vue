<template>
  <div class="file-upload-field">
    <el-input v-model="model" :placeholder="placeholder" :disabled="disabled" />
    <el-upload :show-file-list="false" :http-request="onUpload" :disabled="disabled">
      <el-button type="primary" link :loading="uploading">上传</el-button>
    </el-upload>
    <el-button
      v-if="!disabled && enableEloam"
      type="primary"
      link
      @click="eloamVisible = true"
    >
      高拍仪
    </el-button>
    <el-link v-if="model" :href="fileUrl" target="_blank" type="primary">查看</el-link>
    <EloamCaptureDialog v-model="eloamVisible" :max="1" @done="onEloamDone" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import EloamCaptureDialog from '@/components/form/EloamCaptureDialog.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    disabled?: boolean
    /** 是否显示高拍仪入口（PLT-CAM-01）；默认开启 */
    enableEloam?: boolean
  }>(),
  { enableEloam: true }
)
const emit = defineEmits<{ 'update:modelValue': [v: string] }>()
const uploading = ref(false)
const eloamVisible = ref(false)

const model = computed({
  get: () => props.modelValue ?? '',
  set: (v) => emit('update:modelValue', v)
})

const fileUrl = computed(() => {
  const v = model.value
  if (!v) return ''
  return v.startsWith('http') || v.startsWith('/api') ? v : `/api${v}`
})

async function onUpload(options: { file: File }) {
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', options.file)
    const { data } = await http.post('/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code === 0 && data.data?.url) {
      model.value = data.data.url
      ElMessage.success('上传成功')
    } else {
      ElMessage.error(data.message || '上传失败')
    }
  } catch {
    ElMessage.error('上传失败，请确认文件服务已启动')
  } finally {
    uploading.value = false
  }
}

function onEloamDone(urls: string[]) {
  if (urls[0]) model.value = urls[0]
}
</script>

<style scoped>
.file-upload-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  flex-wrap: wrap;
}
.file-upload-field .el-input {
  flex: 1;
  min-width: 160px;
}
</style>
