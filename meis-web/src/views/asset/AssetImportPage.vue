<template>
  <div class="asset-import-page">
    <SystemPageCard title="资产导入">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="批量导入设备台账"
        description="下载 Excel 模板，按列名填写后上传。支持标准字段与扩展列；主数据（科室、厂商、供应商、分类）可按编码或名称自动匹配。"
        class="import-hint"
      />
      <div class="import-actions">
        <el-button type="primary" @click="downloadTemplate">下载导入模板</el-button>
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls,.csv"
          :file-list="fileList"
          @change="onFileChange"
          @remove="onRemove"
        >
          <el-button>选择文件</el-button>
        </el-upload>
        <el-button type="success" :loading="uploading" :disabled="!selectedFile" @click="submit">开始导入</el-button>
        <el-button @click="$router.push('/asset/device')">查看设备台账</el-button>
      </div>
      <div v-if="result" class="import-result">
        <el-alert
          :title="`成功 ${result.successCount} 条，失败 ${result.failCount} 条`"
          :type="result.failCount ? 'warning' : 'success'"
          show-icon
          :closable="false"
        />
        <ul v-if="result.errors?.length" class="import-errors">
          <li v-for="(err, i) in result.errors.slice(0, 30)" :key="i">{{ err }}</li>
          <li v-if="result.errors.length > 30">… 还有 {{ result.errors.length - 30 }} 条错误</li>
        </ul>
      </div>
    </SystemPageCard>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { downloadApiFile } from '@/utils/fileDownload'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/asset/import')!
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const result = ref<{ successCount: number; failCount: number; errors?: string[] } | null>(null)

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
  await downloadApiFile(config.importTemplateUrl!, 'medical_device_import_template.xlsx')
}

async function submit() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    const { data } = await http.post(config.importUrl!, fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (data.code === 0) {
      result.value = data.data
      ElMessage.success(`导入完成：成功 ${data.data.successCount} 条`)
    }
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.asset-import-page {
  height: 100%;
}

.import-hint {
  margin-bottom: 16px;
}

.import-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.import-result {
  margin-top: 16px;
}

.import-errors {
  margin: 12px 0 0;
  padding-left: 20px;
  color: var(--el-color-danger);
  font-size: 13px;
  max-height: 320px;
  overflow: auto;
}
</style>
