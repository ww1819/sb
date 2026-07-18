<template>
  <div class="table-file-cell">
    <el-upload :show-file-list="false" :http-request="onUpload" :disabled="uploading || !canUpload">
      <el-button link type="primary" :loading="uploading">上传</el-button>
    </el-upload>
    <el-button
      v-if="previewUrl"
      link
      type="primary"
      :loading="previewing"
      @click.stop="onPreview"
    >
      预览
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { openFilePreview } from '@/composables/useFilePreview'

const props = defineProps<{
  value?: unknown
  prop: string
  rowId?: string
  /** 如 /purchase/plan，有值时上传后回写业务附件字段 */
  saveBase?: string
}>()

const emit = defineEmits<{
  updated: [url: string]
}>()

const uploading = ref(false)
const previewing = ref(false)

const previewUrl = computed(() => {
  const v = props.value
  if (v === null || v === undefined || v === '') return ''
  const s = String(v)
  return s.startsWith('http') || s.startsWith('/api') ? s : `/api${s}`
})

const canUpload = computed(() => !!props.rowId && !!props.saveBase)

async function onPreview() {
  if (!previewUrl.value || previewing.value) return
  previewing.value = true
  try {
    await openFilePreview(previewUrl.value, '议价记录预览')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '预览失败'
    ElMessage.error(msg || '预览失败')
  } finally {
    previewing.value = false
  }
}

async function onUpload(options: { file: File }) {
  if (!canUpload.value) {
    ElMessage.warning('无法上传：缺少单据信息')
    return
  }
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', options.file)
    const { data } = await http.post('/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code !== 0 || !data.data?.url) {
      ElMessage.error(data.message || '上传失败')
      return
    }
    const url = String(data.data.url)
    await http.patch(`${props.saveBase}/${props.rowId}/attachment`, {
      field: props.prop,
      url
    })
    emit('updated', url)
    ElMessage.success('上传成功')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.table-file-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}
</style>
