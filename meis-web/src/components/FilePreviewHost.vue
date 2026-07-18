<template>
  <el-dialog
    :model-value="state.visible"
    :title="state.title"
    width="min(900px, 92vw)"
    align-center
    append-to-body
    destroy-on-close
    :z-index="4000"
    :close-on-click-modal="false"
    class="file-preview-dialog"
    @update:model-value="onVisible"
  >
    <div v-loading="state.loading" class="file-preview-dialog__body">
      <el-alert v-if="state.error" type="error" :title="state.error" show-icon :closable="false" />
      <template v-else-if="!state.loading && state.objectUrl">
        <img
          v-if="state.kind === 'image'"
          :src="state.objectUrl"
          class="file-preview-dialog__img"
          alt="附件预览"
        />
        <iframe
          v-else-if="state.kind === 'pdf'"
          :src="state.objectUrl"
          class="file-preview-dialog__frame"
          title="PDF 预览"
        />
        <div v-else class="file-preview-dialog__other">
          <p>该文件类型暂不支持在线预览，请点击下方「下载」保存后查看。</p>
        </div>
      </template>
    </div>
    <template #footer>
      <el-button
        type="primary"
        :disabled="!state.sourceUrl || state.loading"
        :loading="downloading"
        @click="onDownload"
      >
        下载
      </el-button>
      <el-button @click="closeFilePreview">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { closeFilePreview, useFilePreviewState } from '@/composables/useFilePreview'
import { downloadApiFile } from '@/utils/fileDownload'

const state = useFilePreviewState()
const downloading = ref(false)

function onVisible(v: boolean) {
  if (!v) closeFilePreview()
}

async function onDownload() {
  if (!state.sourceUrl || downloading.value) return
  downloading.value = true
  try {
    const name = decodeURIComponent(state.sourceUrl.split('/').pop() || 'attachment')
    if (state.objectUrl) {
      const a = document.createElement('a')
      a.href = state.objectUrl
      a.download = name
      a.click()
    } else {
      await downloadApiFile(state.sourceUrl, name)
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '下载失败')
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.file-preview-dialog__body {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.file-preview-dialog__img {
  max-width: 100%;
  max-height: min(68vh, 680px);
  object-fit: contain;
  display: block;
  margin: 0 auto;
}
.file-preview-dialog__frame {
  width: 100%;
  height: min(68vh, 680px);
  border: 0;
  background: #f5f7fa;
}
.file-preview-dialog__other {
  text-align: center;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}
</style>
