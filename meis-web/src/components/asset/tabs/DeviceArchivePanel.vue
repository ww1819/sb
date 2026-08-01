<template>
  <div class="device-archive-panel">
    <el-alert
      v-if="!deviceId"
      type="info"
      :closable="false"
      show-icon
      title="请先保存设备后再上传档案（将落库保存）"
      class="device-archive-panel__alert"
    />
    <div class="device-archive-panel__toolbar">
      <div class="device-archive-panel__filter">
        <span class="device-archive-panel__filter-label">文档名称</span>
        <el-input v-model="keyword" placeholder="请输入文档名称" clearable class="device-archive-panel__input" />
        <el-button type="primary" @click="load">搜索</el-button>
      </div>
      <div v-if="!readonly && deviceId" class="device-archive-panel__actions">
        <el-upload :show-file-list="false" accept="image/*,.pdf" :http-request="onFileUpload" :disabled="uploading">
          <el-button type="primary" plain :loading="uploading">选择文件</el-button>
        </el-upload>
        <el-button type="primary" @click="eloamVisible = true">高拍仪</el-button>
        <el-button type="warning" plain :disabled="!selectedIds.length" @click="downloadSelected">选中下载</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" @click="removeSelected">选中删除</el-button>
        <el-button type="success" plain :disabled="!previewDoc" @click="openPreview">预览图片</el-button>
      </div>
    </div>

    <div class="device-archive-panel__body">
      <aside class="device-archive-panel__sidebar">
        <div class="device-archive-panel__tree-title">
          <el-icon><Folder /></el-icon>
          <span>文件类型</span>
        </div>
        <ul class="device-archive-panel__tree">
          <li
            v-for="item in fileTypes"
            :key="item"
            :class="{ 'is-active': activeType === item }"
            @click="activeType = item"
          >
            {{ item }}
            <span v-if="item !== '全部文件'" class="cnt">({{ countByType(item) }})</span>
          </li>
        </ul>
      </aside>

      <div class="device-archive-panel__main">
        <div class="device-archive-panel__list">
          <PageEmpty v-if="!filteredDocs.length" description="没有数据" :image-size="72" />
          <el-table
            v-else
            :data="filteredDocs"
            size="small"
            height="100%"
            @selection-change="onSelectionChange"
            @row-click="onRowClick"
          >
            <el-table-column v-if="!readonly" type="selection" width="42" />
            <el-table-column prop="name" label="文档名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="100" />
            <el-table-column prop="source" label="来源" width="80" />
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </div>
        <div class="device-archive-panel__preview">
          <template v-if="previewDoc && isImage(previewDoc.url)">
            <el-image
              :src="resolveUrl(previewDoc.url)"
              fit="contain"
              class="preview-img"
              :preview-src-list="[resolveUrl(previewDoc.url)]"
            />
            <p class="preview-caption">{{ previewDoc.name }}</p>
          </template>
          <p v-else class="device-archive-panel__preview-hint">
            {{ previewDoc ? '当前文件非图片，请下载查看' : '请从列表中选择图片进行预览' }}
          </p>
        </div>
      </div>
    </div>

    <EloamCaptureDialog v-model="eloamVisible" :max="9" @done="onEloamDone" />

    <el-dialog v-model="typePickVisible" title="归类到文件类型" width="400px" append-to-body>
      <el-select v-model="pendingType" style="width: 100%">
        <el-option v-for="t in assignableTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <template #footer>
        <el-button @click="typePickVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmTypedUpload">确定并落库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Folder } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import PageEmpty from '@/components/table/PageEmpty.vue'
import EloamCaptureDialog from '@/components/form/EloamCaptureDialog.vue'
import { formatDisplayDateTime } from '@/utils/datetime'

const props = defineProps<{ deviceId?: string; readonly?: boolean }>()

type ArchiveDoc = {
  id: string
  name: string
  url: string
  fileType: string
  source: string
  createdAt: string
}

const typeToCode: Record<string, string> = {
  合格证: 'other',
  说明书: 'manual',
  技术文档: 'archive',
  其他资料: 'other'
}
const codeToType: Record<string, string> = {
  archive: '技术文档',
  manual: '说明书',
  image: '其他资料',
  other: '其他资料'
}

const keyword = ref('')
const activeType = ref('全部文件')
const fileTypes = ['全部文件', '合格证', '说明书', '技术文档', '其他资料']
const assignableTypes = fileTypes.filter((t) => t !== '全部文件')
const docs = ref<ArchiveDoc[]>([])
const selectedIds = ref<string[]>([])
const previewId = ref<string | null>(null)
const uploading = ref(false)
const saving = ref(false)
const eloamVisible = ref(false)
const typePickVisible = ref(false)
const pendingType = ref('技术文档')
const pendingUrls = ref<string[]>([])
const pendingSource = ref('上传')

const filteredDocs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return docs.value.filter((d) => {
    if (activeType.value !== '全部文件' && d.fileType !== activeType.value) return false
    if (kw && !d.name.toLowerCase().includes(kw)) return false
    return true
  })
})

const previewDoc = computed(() => docs.value.find((d) => d.id === previewId.value) || null)

function countByType(t: string) {
  return docs.value.filter((d) => d.fileType === t).length
}

function resolveUrl(u: string) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('/api') || u.startsWith('data:')) return u
  return `/api${u.startsWith('/') ? '' : '/'}${u}`
}

function isImage(url: string) {
  return /\.(png|jpe?g|gif|webp|bmp)(\?|$)/i.test(url) || url.startsWith('data:image')
}

async function load() {
  if (!props.deviceId) {
    docs.value = []
    return
  }
  const { data } = await http.get(`/asset/device-archive/by-device/${props.deviceId}`)
  const list = (data.data ?? []) as Record<string, unknown>[]
  docs.value = list.map((r) => {
    const remark = String(r.remark ?? '')
    const typed = remark.match(/fileType:([^;]+)/)?.[1]
    const source = remark.match(/source:([^;]+)/)?.[1] || '上传'
    const archiveType = String(r.archive_type ?? 'other')
    return {
      id: String(r.id),
      name: String(r.title || r.file_name || '未命名'),
      url: String(r.file_url ?? ''),
      fileType: typed || codeToType[archiveType] || '其他资料',
      source,
      createdAt: formatDisplayDateTime(r.created_at) || ''
    }
  })
  if (docs.value[0] && !previewId.value) previewId.value = docs.value[0].id
}

function askTypeThenAdd(urls: string[], source: string) {
  if (!urls.length || !props.deviceId) return
  pendingUrls.value = urls
  pendingSource.value = source
  pendingType.value = activeType.value === '全部文件' ? '技术文档' : activeType.value
  typePickVisible.value = true
}

async function confirmTypedUpload() {
  if (!props.deviceId) return
  saving.value = true
  try {
    for (const url of pendingUrls.value) {
      const name = decodeURIComponent(url.split('/').pop() || `档案_${Date.now()}.jpg`)
      await http.post('/asset/device-archive', {
        device_id: props.deviceId,
        archive_type: typeToCode[pendingType.value] || 'other',
        title: name,
        file_url: url,
        file_name: name,
        remark: `fileType:${pendingType.value};source:${pendingSource.value}`
      })
    }
    ElMessage.success(`已保存 ${pendingUrls.value.length} 个文件`)
    pendingUrls.value = []
    typePickVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onFileUpload(options: { file: File }) {
  if (!props.deviceId) {
    ElMessage.warning('请先保存设备')
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
      askTypeThenAdd([String(data.data.url)], '上传')
    } else {
      ElMessage.error(data.message || '上传失败')
    }
  } catch {
    ElMessage.error('上传失败，请确认文件服务可用')
  } finally {
    uploading.value = false
  }
}

function onEloamDone(urls: string[]) {
  askTypeThenAdd(urls, '高拍仪')
}

function onSelectionChange(rows: ArchiveDoc[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function onRowClick(row: ArchiveDoc) {
  previewId.value = row.id
}

function openPreview() {
  if (!previewDoc.value) return
  window.open(resolveUrl(previewDoc.value.url), '_blank')
}

function downloadSelected() {
  for (const id of selectedIds.value) {
    const doc = docs.value.find((d) => d.id === id)
    if (doc) window.open(resolveUrl(doc.url), '_blank')
  }
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个文件？`, '删除', {
    type: 'warning'
  })
  for (const id of selectedIds.value) {
    await http.delete(`/asset/device-archive/${id}`)
  }
  ElMessage.success('已删除')
  selectedIds.value = []
  await load()
}

onMounted(load)
watch(() => props.deviceId, load)
</script>

<style scoped>
.device-archive-panel__alert {
  margin-bottom: 10px;
}
.device-archive-panel__toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.device-archive-panel__filter,
.device-archive-panel__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.device-archive-panel__filter-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.device-archive-panel__input {
  width: 200px;
}
.device-archive-panel__body {
  display: flex;
  gap: 12px;
  min-height: 360px;
}
.device-archive-panel__sidebar {
  width: 160px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 8px;
}
.device-archive-panel__tree-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  margin-bottom: 8px;
}
.device-archive-panel__tree {
  list-style: none;
  margin: 0;
  padding: 0;
}
.device-archive-panel__tree li {
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
}
.device-archive-panel__tree li.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.device-archive-panel__tree .cnt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.device-archive-panel__main {
  flex: 1;
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 12px;
  min-width: 0;
}
.device-archive-panel__list,
.device-archive-panel__preview {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  min-height: 320px;
  padding: 8px;
}
.preview-img {
  width: 100%;
  height: 260px;
}
.preview-caption {
  margin-top: 8px;
  font-size: 12px;
  text-align: center;
}
.device-archive-panel__preview-hint {
  color: var(--el-text-color-secondary);
  text-align: center;
  margin-top: 120px;
}
</style>
