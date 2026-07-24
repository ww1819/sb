<template>
  <div class="image-list-field">
    <div class="thumbs">
      <div v-for="(url, idx) in urls" :key="url + idx" class="thumb">
        <el-image :src="resolveUrl(url)" fit="cover" :preview-src-list="previewList" :initial-index="idx" />
        <el-button
          v-if="!disabled"
          class="remove"
          type="danger"
          link
          @click="removeAt(idx)"
        >
          删除
        </el-button>
      </div>
      <el-upload
        v-if="!disabled && urls.length < max"
        :show-file-list="false"
        accept="image/*"
        :http-request="onUpload"
        :disabled="uploading"
      >
        <div class="add">
          <span v-if="uploading">上传中…</span>
          <span v-else>+ 上传</span>
        </div>
      </el-upload>
      <div
        v-if="!disabled && enableEloam && urls.length < max"
        class="add eloam-add"
        @click="eloamVisible = true"
      >
        高拍仪
      </div>
    </div>
    <div class="hint">最多 {{ max }} 张，非必传；支持普通上传或本机高拍仪</div>
    <EloamCaptureDialog v-model="eloamVisible" :max="remain" @done="onEloamDone" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import EloamCaptureDialog from '@/components/form/EloamCaptureDialog.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: unknown
    disabled?: boolean
    max?: number
    enableEloam?: boolean
  }>(),
  { max: 3, enableEloam: true }
)
const emit = defineEmits<{ 'update:modelValue': [v: string[]] }>()
const uploading = ref(false)
const eloamVisible = ref(false)

const urls = computed(() => {
  const v = props.modelValue
  if (Array.isArray(v)) return v.map(String).filter(Boolean)
  if (typeof v === 'string' && v.trim()) {
    try {
      const parsed = JSON.parse(v)
      if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
    } catch {
      return [v]
    }
  }
  return [] as string[]
})

const remain = computed(() => Math.max(0, props.max - urls.value.length))
const previewList = computed(() => urls.value.map(resolveUrl))

function resolveUrl(u: string) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('/api')) return u
  return `/api${u.startsWith('/') ? '' : '/'}${u}`
}

function setUrls(next: string[]) {
  emit('update:modelValue', next.slice(0, props.max))
}

function removeAt(idx: number) {
  const next = [...urls.value]
  next.splice(idx, 1)
  setUrls(next)
}

async function onUpload(options: { file: File }) {
  if (urls.value.length >= props.max) {
    ElMessage.warning(`最多上传 ${props.max} 张`)
    return
  }
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', options.file)
    const { data } = await http.post('/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code === 0 && data.data?.url) {
      setUrls([...urls.value, String(data.data.url)])
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

function onEloamDone(newUrls: string[]) {
  setUrls([...urls.value, ...newUrls])
}
</script>

<style scoped>
.image-list-field .thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.thumb {
  position: relative;
  width: 88px;
  height: 88px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
}
.thumb :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.thumb .remove {
  position: absolute;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.85);
  padding: 0 4px;
}
.add {
  width: 88px;
  height: 88px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 13px;
}
.eloam-add {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}
.hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
