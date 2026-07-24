<template>
  <div class="device-archive-panel">
    <div class="device-archive-panel__toolbar">
      <div class="device-archive-panel__filter">
        <span class="device-archive-panel__filter-label">文档名称</span>
        <el-input v-model="keyword" placeholder="请输入文档名称" clearable class="device-archive-panel__input" />
        <el-button type="primary">搜索</el-button>
      </div>
      <div class="device-archive-panel__actions" v-if="!readonly">
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
            <el-image :src="resolveUrl(previewDoc.url)" fit="contain" class="preview-img" :preview-src-list="[resolveUrl(previewDoc.url)]" />
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
        <el-button type="primary" @click="confirmTypedUpload">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Folder } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import PageEmpty from '@/components/table/PageEmpty.vue'
import EloamCaptureDialog from '@/components/form/EloamCaptureDialog.vue'

defineProps<{ readonly?: boolean }>()

type ArchiveDoc = {
  id: string
  name: string
  url: string
  fileType: string
  source: string
  createdAt: string
}

const keyword = ref('')
const activeType = ref('全部文件')
const fileTypes = ['全部文件', '合格证', '说明书', '验收资料', '其他资料']
const assignableTypes = fileTypes.filter((t) => t !== '全部文件')
const docs = ref<ArchiveDoc[]>([])
const selectedIds = ref<string[]>([])
const previewId = ref<string | null>(null)
const uploading = ref(false)
const eloamVisible = ref(false)
const typePickVisible = ref(false)
const pendingType = ref('其他资料')
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

function nowStr() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function addDocs(urls: string[], fileType: string, source: string) {
  for (const url of urls) {
    const name = url.split('/').pop() || `档案_${Date.now()}.jpg`
    docs.value.unshift({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      name: decodeURIComponent(name),
      url,
      fileType,
      source,
      createdAt: nowStr()
    })
  }
  if (docs.value[0]) previewId.value = docs.value[0].id
  ElMessage.success(`已加入 ${urls.length} 个文件（${fileType}）`)
}

function askTypeThenAdd(urls: string[], source: string) {
  if (!urls.length) return
  pendingUrls.value = urls
  pendingSource.value = source
  pendingType.value = activeType.value === '全部文件' ? '其他资料' : activeType.value
  typePickVisible.value = true
}

function confirmTypedUpload() {
  addDocs(pendingUrls.value, pendingType.value, pendingSource.value)
  pendingUrls.value = []
  typePickVisible.value = false
}

async function onFileUpload(options: { file: File }) {
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
    ElMessage.error('上传失败，请确认文件服务已启动')
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
  if (!isImage(previewDoc.value.url)) {
    window.open(resolveUrl(previewDoc.value.url), '_blank')
    return
  }
  /* el-image 自带预览；再开一次窗口便于大图 */
  window.open(resolveUrl(previewDoc.value.url), '_blank')
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个文件？`, '删除', {
      type: 'warning'
    })
  } catch {
    return
  }
  const set = new Set(selectedIds.value)
  docs.value = docs.value.filter((d) => !set.has(d.id))
  if (previewId.value && set.has(previewId.value)) previewId.value = null
  selectedIds.value = []
  ElMessage.success('已删除')
}

function downloadSelected() {
  const rows = docs.value.filter((d) => selectedIds.value.includes(d.id))
  if (!rows.length) return
  for (const r of rows) {
    const a = document.createElement('a')
    a.href = resolveUrl(r.url)
    a.download = r.name
    a.target = '_blank'
    a.rel = 'noopener'
    a.click()
  }
}
</script>

<style scoped>
.device-archive-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 360px;
}

.device-archive-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  background: #fafbfc;
}

.device-archive-panel__filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-archive-panel__filter-label {
  font-size: 13px;
  color: var(--meis-text-secondary);
  white-space: nowrap;
}

.device-archive-panel__input {
  width: 220px;
}

.device-archive-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.device-archive-panel__body {
  flex: 1;
  min-height: 320px;
  display: flex;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  overflow: hidden;
  background: #fff;
}

.device-archive-panel__sidebar {
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid var(--meis-border-light);
  background: #fafbfc;
}

.device-archive-panel__tree-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--meis-text-primary);
  border-bottom: 1px solid var(--meis-border-light);
}

.device-archive-panel__tree {
  margin: 0;
  padding: 8px 0;
  list-style: none;
}

.device-archive-panel__tree li {
  padding: 8px 14px 8px 28px;
  font-size: 13px;
  color: var(--meis-text-secondary);
  cursor: pointer;
}

.device-archive-panel__tree li .cnt {
  margin-left: 4px;
  color: var(--meis-text-secondary);
  font-size: 12px;
}

.device-archive-panel__tree li:hover,
.device-archive-panel__tree li.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.device-archive-panel__main {
  flex: 1;
  min-width: 0;
  display: flex;
}

.device-archive-panel__list {
  flex: 1;
  min-width: 0;
  border-right: 1px solid var(--meis-border-light);
  display: flex;
  align-items: stretch;
  justify-content: center;
  min-height: 280px;
  padding: 8px;
}

.device-archive-panel__preview {
  width: 42%;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #fcfcfd;
  gap: 8px;
}

.preview-img {
  width: 100%;
  max-height: 280px;
}

.preview-caption {
  margin: 0;
  font-size: 12px;
  color: var(--meis-text-secondary);
}

.device-archive-panel__preview-hint {
  margin: 0;
  padding: 48px 24px;
  width: 100%;
  text-align: center;
  font-size: 13px;
  color: var(--meis-text-secondary);
  border: 1px dashed #dcdfe6;
  border-radius: var(--meis-card-radius);
  background: #fff;
}

@media (max-width: 1100px) {
  .device-archive-panel__main {
    flex-direction: column;
  }

  .device-archive-panel__list {
    border-right: none;
    border-bottom: 1px solid var(--meis-border-light);
    min-height: 180px;
  }

  .device-archive-panel__preview {
    width: 100%;
    min-width: 0;
  }
}
</style>
