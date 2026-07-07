<template>
  <el-dialog v-model="visible" :title="title" width="520px" destroy-on-close @closed="reset">
    <p class="import-tip">请上传 Excel（.xlsx）文件。可先下载模板，按「导入数据」页签列名填写后导入。仍兼容 CSV。模板含「字段说明」页签；不同客户可通过后台配置扩展列。</p>
    <div class="import-actions">
      <el-button @click="downloadTemplate">下载导入模板</el-button>
    </div>
    <el-upload
      drag
      :auto-upload="false"
      :limit="1"
      accept=".xlsx,.xls,.csv"
      :file-list="fileList"
      @change="onFileChange"
      @remove="onRemove"
    >
      <div class="el-upload__text">将 Excel 拖到此处，或<em>点击选择</em></div>
    </el-upload>
    <div v-if="result" class="import-result">
      <el-alert
        :title="`成功 ${result.successCount} 条，失败 ${result.failCount} 条`"
        :type="result.failCount ? 'warning' : 'success'"
        show-icon
        :closable="false"
      />
      <ul v-if="result.errors?.length" class="import-errors">
        <li v-for="(err, i) in result.errors.slice(0, 20)" :key="i">{{ err }}</li>
        <li v-if="result.errors.length > 20">… 还有 {{ result.errors.length - 20 }} 条错误</li>
      </ul>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="submit">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import http from '@/api/http'
import { downloadApiFile } from '@/utils/fileDownload'

export interface ImportResultData {
  successCount: number
  failCount: number
  errors?: string[]
}

const props = defineProps<{
  modelValue: boolean
  title?: string
  importUrl: string
  templateUrl: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = ref(false)
const uploading = ref(false)
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File | null>(null)
const result = ref<ImportResultData | null>(null)

watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

function onFileChange(file: UploadFile) {
  selectedFile.value = file.raw ?? null
  fileList.value = file.raw ? [file as UploadUserFile] : []
  result.value = null
}

function onRemove() {
  selectedFile.value = null
  fileList.value = []
}

async function downloadTemplate() {
  try {
    const name = props.templateUrl.split('/').filter(Boolean).slice(-2)[0] || 'template'
    await downloadApiFile(props.templateUrl, `${name}_import_template.xlsx`)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

async function submit() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const { data } = await http.post(props.importUrl, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code !== 0) {
      ElMessage.error(data.message || '导入失败')
      return
    }
    result.value = data.data
    if (data.data.failCount === 0) {
      ElMessage.success(`导入成功 ${data.data.successCount} 条`)
      emit('success')
    } else {
      ElMessage.warning(`导入完成：成功 ${data.data.successCount} 条，失败 ${data.data.failCount} 条`)
      if (data.data.successCount > 0) emit('success')
    }
  } catch {
    // http 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function reset() {
  fileList.value = []
  selectedFile.value = null
  result.value = null
  uploading.value = false
}
</script>

<style scoped>
.import-tip {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.import-actions {
  margin-bottom: 12px;
}
.import-result {
  margin-top: 16px;
}
.import-errors {
  margin: 12px 0 0;
  padding-left: 18px;
  max-height: 180px;
  overflow: auto;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
