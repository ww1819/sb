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
        <span class="device-archive-panel__filter-label">排序</span>
        <el-select v-model="sortMode" style="width: 130px" @change="onSortModeChange">
          <el-option label="自定义排序" value="custom" />
          <el-option label="上传时间" value="time" />
          <el-option label="名称" value="name" />
        </el-select>
        <el-button type="primary" @click="load">搜索</el-button>
      </div>
      <div v-if="!readonly && deviceId" class="device-archive-panel__actions">
        <el-upload :show-file-list="false" accept="image/*,.pdf" :http-request="onFileUpload" :disabled="uploading">
          <el-button type="primary" plain :loading="uploading">选择文件</el-button>
        </el-upload>
        <el-button type="primary" @click="eloamVisible = true">高拍仪</el-button>
        <el-button plain :disabled="!editableSelectedIds.length" @click="copySelected">选中复制到…</el-button>
        <el-button plain :disabled="!editableSelectedIds.length" @click="openBatchReclass">批量改类</el-button>
        <el-button plain :disabled="sortMode !== 'custom'" @click="openSortDrawer">排序调整</el-button>
        <el-button type="warning" plain :disabled="!selectedIds.length" @click="downloadSelected">选中下载</el-button>
        <el-button type="danger" plain :disabled="!editableSelectedIds.length" @click="removeSelected">选中删除</el-button>
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
            v-for="item in fileTypeTabs"
            :key="item.value"
            :class="{ 'is-active': activeType === item.value }"
            @click="activeType = item.value"
          >
            {{ item.label }}
            <span v-if="item.value !== 'all'" class="cnt">({{ countByType(item.value) }})</span>
          </li>
        </ul>
      </aside>

      <div class="device-archive-panel__main">
        <div class="device-archive-panel__list">
          <PageEmpty v-if="!filteredDocs.length" description="没有数据" :image-size="72" />
          <el-table
            v-else
            :data="filteredDocs"
            row-key="id"
            size="small"
            height="100%"
            @selection-change="onSelectionChange"
            @row-click="onRowClick"
          >
            <el-table-column v-if="!readonly" type="selection" width="42" :selectable="rowSelectable" />
            <el-table-column prop="name" label="文档名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="fileTypeLabel" label="类型" width="100" />
            <el-table-column prop="sourceLabel" label="来源" width="90" />
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column v-if="!readonly" label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="!row.readonly">
                  <el-button link type="primary" @click.stop="renameDoc(row)">改名</el-button>
                  <el-button link type="primary" @click.stop="reclassifyDoc(row)">改类</el-button>
                  <el-button v-if="isImage(row.url)" link type="primary" @click.stop="rotateDoc(row)">旋转</el-button>
                </template>
                <el-tag v-else size="small" type="info">只读</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="device-archive-panel__preview">
          <template v-if="previewDoc && isImage(previewDoc.url)">
            <div class="preview-frame" @wheel.prevent="onPreviewWheel">
              <img
                :src="resolveUrl(previewDoc.url)"
                class="preview-img"
                :style="{
                  transform: `rotate(${draftRotate}deg) scale(${previewZoom})`
                }"
                alt=""
              />
            </div>
            <p class="preview-caption">{{ previewDoc.name }}</p>
            <div v-if="!readonly && !previewDoc.readonly" class="preview-actions">
              <el-button size="small" @click="draftRotate = (draftRotate + 270) % 360">左旋</el-button>
              <el-button size="small" @click="draftRotate = (draftRotate + 90) % 360">右旋</el-button>
              <el-button size="small" @click="resetPreviewAngle">还原</el-button>
              <el-button size="small" type="primary" @click="savePreviewAngle">保存角度</el-button>
              <el-button size="small" @click="previewZoom = Math.min(3, previewZoom + 0.1)">放大</el-button>
              <el-button size="small" @click="previewZoom = Math.max(0.4, previewZoom - 0.1)">缩小</el-button>
              <el-button size="small" @click="openPreview">新窗口</el-button>
            </div>
          </template>
          <template v-else-if="previewDoc && isPdf(previewDoc.url)">
            <iframe :src="resolveUrl(previewDoc.url)" class="preview-pdf" title="PDF 预览" />
            <p class="preview-caption">{{ previewDoc.name }}</p>
            <div class="preview-actions">
              <el-button size="small" @click="openPreview">新窗口</el-button>
            </div>
          </template>
          <p v-else class="device-archive-panel__preview-hint">
            {{ previewDoc ? '当前文件请下载查看' : '请从列表中选择文件进行预览' }}
          </p>
        </div>
      </div>
    </div>

    <EloamCaptureDialog v-model="eloamVisible" :max="9" @done="onEloamDone" />
    <AssetDevicePicker v-model="pickerVisible" multiple @confirm-many="onCopyTargets" />

    <el-dialog v-model="typePickVisible" title="归类到文件类型" width="400px" append-to-body>
      <el-select v-model="pendingType" style="width: 100%">
        <el-option v-for="t in assignableTypes" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <template #footer>
        <el-button @click="typePickVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmTypedUpload">确定并落库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reclassVisible" :title="batchReclass ? '批量修改档案类型' : '修改档案类型'" width="400px" append-to-body>
      <el-select v-model="reclassType" style="width: 100%">
        <el-option v-for="t in assignableTypes" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <template #footer>
        <el-button @click="reclassVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmReclassify">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sortDrawerVisible" title="排序调整（当前类型内）" width="560px" append-to-body>
      <p class="sort-hint">拖动序号或使用上移/下移；可改显示名称。仅作用于当前侧栏类型（非「全部」）。</p>
      <el-table :data="sortRows" size="small" max-height="360">
        <el-table-column label="序" width="70">
          <template #default="{ $index }">
            <el-input-number v-model="sortRows[$index].seq" :min="1" :max="sortRows.length" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ $index }">
            <el-button link @click="swapSort($index, -1)">上</el-button>
            <el-button link @click="swapSort($index, 1)">下</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="sortDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSortDrawer">保存排序</el-button>
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
import AssetDevicePicker from '@/components/form/AssetDevicePicker.vue'
import { useDict } from '@/composables/useDict'
import { formatDisplayDateTime } from '@/utils/datetime'

const props = defineProps<{ deviceId?: string; readonly?: boolean }>()

type ArchiveDoc = {
  /** 档案行=真实 UUID；引用行=client_key（非主键） */
  id: string
  rowKind: 'archive' | 'ref'
  name: string
  url: string
  archiveType: string
  fileTypeLabel: string
  source: string
  sourceLabel: string
  rotateAngle: number
  sortOrder: number
  createdAt: string
  readonly?: boolean
}

const SOURCE_LABEL: Record<string, string> = {
  upload: '上传',
  eloam: '高拍仪',
  copy: '复制',
  contract: '合同'
}

const SORT_PREF_KEY = 'meis_device_archive_sort'

const keyword = ref('')
const sortMode = ref<'custom' | 'time' | 'name'>(
  (localStorage.getItem(SORT_PREF_KEY) as 'custom' | 'time' | 'name') || 'custom'
)
const activeType = ref('all')
const docs = ref<ArchiveDoc[]>([])
const selectedIds = ref<string[]>([])
const previewId = ref<string | null>(null)
const uploading = ref(false)
const saving = ref(false)
const eloamVisible = ref(false)
const pickerVisible = ref(false)
const typePickVisible = ref(false)
const pendingType = ref('tech_doc')
const pendingUrls = ref<string[]>([])
const pendingSource = ref('upload')
const typeOptions = ref<{ label: string; value: string }[]>([])
const reclassVisible = ref(false)
const reclassType = ref('tech_doc')
const reclassId = ref<string | null>(null)
const batchReclass = ref(false)
const sortDrawerVisible = ref(false)
const sortRows = ref<{ id: string; name: string; seq: number }[]>([])
const draftRotate = ref(0)
const previewZoom = ref(1)

const { loadDict, resolveLabel } = useDict()

const fileTypeTabs = computed(() => [{ label: '全部文件', value: 'all' }, ...typeOptions.value])
const assignableTypes = computed(() => typeOptions.value)

const filteredDocs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return docs.value.filter((d) => {
    if (activeType.value !== 'all' && d.archiveType !== activeType.value) return false
    if (kw && !d.name.toLowerCase().includes(kw)) return false
    return true
  })
})

const previewDoc = computed(() => docs.value.find((d) => d.id === previewId.value) || null)
const editableSelectedIds = computed(() =>
  selectedIds.value.filter((id) => {
    const d = docs.value.find((x) => x.id === id)
    return d && d.rowKind === 'archive' && !d.readonly
  })
)

function countByType(t: string) {
  return docs.value.filter((d) => d.archiveType === t).length
}

function resolveUrl(u: string) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('/api') || u.startsWith('data:')) return u
  return `/api${u.startsWith('/') ? '' : '/'}${u}`
}

function isImage(url: string) {
  return /\.(png|jpe?g|gif|webp|bmp)(\?|$)/i.test(url) || url.startsWith('data:image')
}

function isPdf(url: string) {
  return /\.pdf(\?|$)/i.test(url)
}

function rowSelectable(row: ArchiveDoc) {
  return row.rowKind === 'archive' && !row.readonly
}

function onSortModeChange() {
  localStorage.setItem(SORT_PREF_KEY, sortMode.value)
  load()
}

watch(previewDoc, (d) => {
  draftRotate.value = d?.rotateAngle ?? 0
  previewZoom.value = 1
})

async function load() {
  if (!props.deviceId) {
    docs.value = []
    return
  }
  const { data } = await http.get(`/asset/device-archive/by-device/${props.deviceId}`, {
    params: { sort: sortMode.value }
  })
  const list = (data.data ?? []) as Record<string, unknown>[]
  docs.value = list.map((r) => {
    const archiveType = String(r.archive_type ?? 'other')
    const source = String(r.source ?? 'upload')
    const rowKind = String(r.row_kind ?? 'archive') === 'ref' ? 'ref' : 'archive'
    const clientKey = r.client_key != null ? String(r.client_key) : ''
    const rawId = r.id != null && String(r.id) !== 'null' ? String(r.id) : ''
    const id = rowKind === 'ref' ? clientKey || `ref:${rawId || Math.random()}` : rawId
    const readonly = rowKind === 'ref' || !!r.readonly || source === 'contract'
    return {
      id,
      rowKind,
      name: String(r.title || r.file_name || '未命名'),
      url: String(r.file_url ?? ''),
      archiveType,
      fileTypeLabel: resolveLabel('device_archive_type', archiveType) || archiveType,
      source,
      sourceLabel: SOURCE_LABEL[source] || source,
      rotateAngle: Number(r.rotate_angle ?? 0) || 0,
      sortOrder: Number(r.sort_order ?? 0) || 0,
      createdAt: formatDisplayDateTime(r.created_at) || '',
      readonly
    }
  })
  if (docs.value[0] && !previewId.value) previewId.value = docs.value[0].id
}

function askTypeThenAdd(urls: string[], source: string) {
  if (!urls.length || !props.deviceId) return
  pendingUrls.value = urls
  pendingSource.value = source
  pendingType.value =
    activeType.value !== 'all' ? activeType.value : typeOptions.value[0]?.value || 'tech_doc'
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
        archive_type: pendingType.value,
        title: name,
        file_url: url,
        file_name: name,
        source: pendingSource.value
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
      askTypeThenAdd([String(data.data.url)], 'upload')
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
  askTypeThenAdd(urls, 'eloam')
}

function onSelectionChange(rows: ArchiveDoc[]) {
  selectedIds.value = rows.map((r) => r.id)
}

function onRowClick(row: ArchiveDoc) {
  previewId.value = row.id
}

function openPreview() {
  if (!previewDoc.value) return
  const url = resolveUrl(previewDoc.value.url)
  if (isImage(previewDoc.value.url) && draftRotate.value) {
    // 新窗无法带 CSS 角：提示以侧栏为准，仍打开原图
    ElMessage.info('新窗口为原图方向；带旋转角请以侧栏预览为准')
  }
  window.open(url, '_blank')
}

function downloadUrl(url: string, name: string) {
  const u = resolveUrl(url)
  const sep = u.includes('?') ? '&' : '?'
  const href = `${u}${sep}disposition=attachment&filename=${encodeURIComponent(name)}`
  const a = document.createElement('a')
  a.href = href
  a.download = name
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

function downloadSelected() {
  for (const id of selectedIds.value) {
    const doc = docs.value.find((d) => d.id === id)
    if (doc) downloadUrl(doc.url, doc.name)
  }
}

async function removeSelected() {
  if (!editableSelectedIds.value.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${editableSelectedIds.value.length} 个文件？`, '删除', {
    type: 'warning'
  })
  await http.post('/asset/device-archive/batch-delete', { ids: editableSelectedIds.value })
  ElMessage.success('已删除')
  selectedIds.value = []
  await load()
}

function copySelected() {
  if (!editableSelectedIds.value.length) return
  pickerVisible.value = true
}

async function onCopyTargets(devices: Record<string, unknown>[]) {
  const targetIds = devices.map((d) => String(d.id || '')).filter(Boolean)
  const filtered = targetIds.filter((id) => id !== props.deviceId)
  if (!filtered.length) {
    ElMessage.warning('请选择其他设备作为复制目标')
    return
  }
  const { data } = await http.post('/asset/device-archive/copy-to', {
    ids: editableSelectedIds.value,
    target_device_ids: filtered
  })
  if (data.code === 0) {
    ElMessage.success(`已复制 ${data.data?.created ?? 0} 个文件到 ${filtered.length} 台设备`)
  } else {
    ElMessage.error(data.message || '复制失败')
  }
}

async function renameDoc(row: ArchiveDoc) {
  const extMatch = row.name.match(/(\.[^.]+)$/)
  const ext = extMatch ? extMatch[1] : ''
  const base = ext ? row.name.slice(0, -ext.length) : row.name
  const { value } = await ElMessageBox.prompt(`文档名称${ext ? `（扩展名 ${ext} 将保留）` : ''}`, '修改名称', {
    inputValue: base,
    confirmButtonText: '保存',
    cancelButtonText: '取消'
  })
  const name = String(value || '').trim()
  if (!name) return
  const full = name.endsWith(ext) || !ext ? name : name + ext
  await http.put(`/asset/device-archive/${row.id}`, { title: full, file_name: full })
  ElMessage.success('已改名')
  await load()
}

function reclassifyDoc(row: ArchiveDoc) {
  batchReclass.value = false
  reclassId.value = row.id
  reclassType.value = row.archiveType
  reclassVisible.value = true
}

function openBatchReclass() {
  if (!editableSelectedIds.value.length) return
  batchReclass.value = true
  reclassId.value = null
  reclassType.value = assignableTypes.value[0]?.value || 'tech_doc'
  reclassVisible.value = true
}

async function confirmReclassify() {
  saving.value = true
  try {
    if (batchReclass.value) {
      await http.post('/asset/device-archive/batch-reclassify', {
        ids: editableSelectedIds.value,
        archive_type: reclassType.value
      })
    } else if (reclassId.value) {
      await http.put(`/asset/device-archive/${reclassId.value}`, { archive_type: reclassType.value })
    }
    ElMessage.success('已改类')
    reclassVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function rotateDoc(row: ArchiveDoc) {
  const { data } = await http.post(`/asset/device-archive/${row.id}/rotate`, {})
  if (data.code === 0) {
    row.rotateAngle = Number(data.data?.rotate_angle ?? (row.rotateAngle + 90) % 360)
    if (previewDoc.value?.id === row.id) draftRotate.value = row.rotateAngle
    ElMessage.success('已旋转')
  } else {
    ElMessage.error(data.message || '旋转失败')
  }
}

function resetPreviewAngle() {
  draftRotate.value = previewDoc.value?.rotateAngle ?? 0
  previewZoom.value = 1
}

async function savePreviewAngle() {
  if (!previewDoc.value || previewDoc.value.readonly) return
  await http.post(`/asset/device-archive/${previewDoc.value.id}/rotate`, {
    rotate_angle: draftRotate.value
  })
  previewDoc.value.rotateAngle = draftRotate.value
  ElMessage.success('已保存旋转角')
  await load()
}

function onPreviewWheel(e: WheelEvent) {
  if (e.deltaY < 0) previewZoom.value = Math.min(3, previewZoom.value + 0.1)
  else previewZoom.value = Math.max(0.4, previewZoom.value - 0.1)
}

function openSortDrawer() {
  if (activeType.value === 'all') {
    ElMessage.info('请先选择左侧某一档案类型再调整排序')
    return
  }
  const list = docs.value.filter((d) => d.archiveType === activeType.value && !d.readonly)
  if (!list.length) {
    ElMessage.info('当前类型无可排序文件')
    return
  }
  sortRows.value = list.map((d, i) => ({ id: d.id, name: d.name, seq: i + 1 }))
  sortDrawerVisible.value = true
}

function swapSort(index: number, delta: number) {
  const j = index + delta
  if (j < 0 || j >= sortRows.value.length) return
  const arr = [...sortRows.value]
  const t = arr[index]
  arr[index] = arr[j]
  arr[j] = t
  sortRows.value = arr.map((r, i) => ({ ...r, seq: i + 1 }))
}

async function saveSortDrawer() {
  saving.value = true
  try {
    const ordered = [...sortRows.value].sort((a, b) => a.seq - b.seq)
    await http.post('/asset/device-archive/save-sort', { ids: ordered.map((r) => r.id) })
    for (const r of ordered) {
      const doc = docs.value.find((d) => d.id === r.id)
      if (doc && doc.name !== r.name) {
        await http.put(`/asset/device-archive/${r.id}`, { title: r.name, file_name: r.name })
      }
    }
    ElMessage.success('排序已保存')
    sortDrawerVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  typeOptions.value = (await loadDict('device_archive_type')) ?? []
  await load()
})
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
.preview-frame {
  width: 100%;
  height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f5f7fa;
}
.preview-img {
  max-width: 100%;
  max-height: 260px;
  object-fit: contain;
  transition: transform 0.15s ease;
}
.preview-pdf {
  width: 100%;
  height: 280px;
  border: none;
  background: #f5f7fa;
}
.preview-caption {
  margin-top: 8px;
  font-size: 12px;
  text-align: center;
}
.preview-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.device-archive-panel__preview-hint {
  color: var(--el-text-color-secondary);
  text-align: center;
  margin-top: 120px;
}
.sort-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 10px;
}
</style>
