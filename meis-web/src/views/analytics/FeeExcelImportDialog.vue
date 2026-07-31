<template>
  <el-dialog
    v-model="visible"
    title="费用 Excel 导入"
    width="860px"
    destroy-on-close
    append-to-body
    @closed="reset"
  >
    <p class="import-tip">
      请先下载模板，按列填写后上传；点击「解析模板」预览数据，确认无误后再「导入」。本期为前端示意，不接后台。
    </p>

    <div class="import-toolbar">
      <el-button @click="downloadTemplate">下载模板</el-button>
      <el-upload
        class="file-upload"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        :show-file-list="false"
        :file-list="fileList"
        @change="onFileChange"
      >
        <el-button>选择文件</el-button>
      </el-upload>
      <span class="file-name" :title="selectedFile?.name || ''">
        {{ selectedFile?.name || '未选择文件' }}
      </span>
      <el-button type="primary" plain :disabled="!selectedFile" :loading="parsing" @click="parseTemplate">
        解析模板
      </el-button>
      <el-button type="primary" :disabled="!parsedRows.length" :loading="importing" @click="doImport">
        导入
      </el-button>
    </div>

    <div v-if="parseErrors.length" class="parse-errors">
      <el-alert
        :title="`解析告警 ${parseErrors.length} 条`"
        type="warning"
        show-icon
        :closable="false"
      />
      <ul>
        <li v-for="(err, i) in parseErrors.slice(0, 8)" :key="i">{{ err }}</li>
        <li v-if="parseErrors.length > 8">… 还有 {{ parseErrors.length - 8 }} 条</li>
      </ul>
    </div>

    <div class="preview-wrap">
      <div class="preview-title">
        解析预览
        <span v-if="parsedRows.length" class="preview-count">共 {{ parsedRows.length }} 行</span>
      </div>
      <el-table
        :data="parsedRows"
        border
        stripe
        height="320"
        size="small"
        empty-text="请先选择文件并解析模板"
      >
        <el-table-column
          v-for="col in TEMPLATE_COLUMNS"
          :key="col"
          :prop="col"
          :label="col"
          min-width="100"
          show-overflow-tooltip
        />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { downloadExcelHtml, escapeHtml } from '@/utils/excelHtmlExport'

export type FeeImportRow = Record<string, string>

const TEMPLATE_COLUMNS = [
  '设备编号',
  '设备名称',
  '统计年月',
  '收入(月)',
  '材料费(月)',
  '折旧费',
  '人员费',
  '维保费',
  '水电费(月)',
  '管理费',
  '利息支出',
  '维修停机天数',
  '诊疗人次',
  '场地费',
  '应开机工作日',
  '月工作量',
  '使用率(%)',
  '完好率(%)',
  '阳性率(%)',
  '其他'
] as const

const SAMPLE_ROW: FeeImportRow = {
  设备编号: 'SB-0001',
  设备名称: '多参数监护仪',
  统计年月: '2026-07',
  '收入(月)': '12000',
  '材料费(月)': '800',
  折旧费: '1500',
  人员费: '2000',
  维保费: '300',
  '水电费(月)': '200',
  管理费: '100',
  利息支出: '0',
  维修停机天数: '1',
  诊疗人次: '120',
  场地费: '500',
  应开机工作日: '22',
  月工作量: '200',
  '使用率(%)': '85',
  '完好率(%)': '98',
  '阳性率(%)': '12',
  其他: '50'
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  imported: [rows: FeeImportRow[]]
}>()

const visible = ref(false)
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File | null>(null)
const parsing = ref(false)
const importing = ref(false)
const parsedRows = ref<FeeImportRow[]>([])
const parseErrors = ref<string[]>([])

watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
  }
)
watch(visible, (v) => emit('update:modelValue', v))

function onFileChange(file: UploadFile) {
  selectedFile.value = file.raw ?? null
  fileList.value = file.raw ? [file as UploadUserFile] : []
  parsedRows.value = []
  parseErrors.value = []
}

function downloadTemplate() {
  const head = TEMPLATE_COLUMNS.map((c) => `<th>${escapeHtml(c)}</th>`).join('')
  const body = TEMPLATE_COLUMNS.map((c) => `<td>${escapeHtml(SAMPLE_ROW[c] ?? '')}</td>`).join('')
  const table = `<table border="1"><thead><tr>${head}</tr></thead><tbody><tr>${body}</tr></tbody></table>`
  downloadExcelHtml(table, '费用手工登记导入模板')
  ElMessage.success('模板已下载')
}

function splitCsvLine(line: string): string[] {
  const cells: string[] = []
  let cur = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"'
        i++
      } else {
        inQuotes = !inQuotes
      }
      continue
    }
    if ((ch === ',' || ch === '\t') && !inQuotes) {
      cells.push(cur.trim())
      cur = ''
      continue
    }
    cur += ch
  }
  cells.push(cur.trim())
  return cells
}

function rowsFromMatrix(matrix: string[][]): { rows: FeeImportRow[]; errors: string[] } {
  const errors: string[] = []
  if (!matrix.length) {
    return { rows: [], errors: ['文件无有效内容'] }
  }
  const header = matrix[0].map((h) => h.replace(/^\uFEFF/, '').trim())
  const missing = TEMPLATE_COLUMNS.filter((c) => !header.includes(c))
  if (missing.length) {
    errors.push(`缺少列：${missing.slice(0, 6).join('、')}${missing.length > 6 ? '…' : ''}`)
  }
  const rows: FeeImportRow[] = []
  for (let r = 1; r < matrix.length; r++) {
    const cells = matrix[r]
    if (!cells.some((c) => c.trim())) continue
    const row: FeeImportRow = {}
    for (const col of TEMPLATE_COLUMNS) {
      const idx = header.indexOf(col)
      row[col] = idx >= 0 ? (cells[idx] ?? '').trim() : ''
    }
    if (!row['设备编号']) {
      errors.push(`第 ${r + 1} 行缺少设备编号，已跳过`)
      continue
    }
    rows.push(row)
  }
  if (!rows.length && !errors.length) errors.push('未解析到有效数据行')
  return { rows, errors }
}

function parseHtmlTable(text: string): string[][] {
  const doc = new DOMParser().parseFromString(text, 'text/html')
  const trs = [...doc.querySelectorAll('tr')]
  return trs.map((tr) =>
    [...tr.querySelectorAll('th,td')].map((cell) => (cell.textContent || '').trim())
  )
}

function parsePlainText(text: string): string[][] {
  const lines = text
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .map((l) => l.trimEnd())
    .filter((l) => l.length > 0)
  return lines.map(splitCsvLine)
}

async function parseTemplate() {
  if (!selectedFile.value) return
  parsing.value = true
  parsedRows.value = []
  parseErrors.value = []
  try {
    const text = await selectedFile.value.text()
    const name = selectedFile.value.name.toLowerCase()
    let matrix: string[][] = []
    if (name.endsWith('.xls') || text.includes('<table') || text.includes('<html')) {
      matrix = parseHtmlTable(text)
      if (!matrix.length || matrix.every((r) => !r.length)) {
        matrix = parsePlainText(text)
      }
    } else {
      matrix = parsePlainText(text)
    }
    // 真 .xlsx 多为二进制，text() 会乱码
    if (name.endsWith('.xlsx') && (!matrix.length || matrix[0].every((c) => !/[\u4e00-\u9fa5a-zA-Z]/.test(c)))) {
      ElMessage.error('暂不支持原生 .xlsx，请使用「下载模板」生成的 .xls，或另存为 CSV 后上传')
      return
    }
    const { rows, errors } = rowsFromMatrix(matrix)
    parsedRows.value = rows
    parseErrors.value = errors
    if (rows.length) {
      ElMessage.success(`解析完成，共 ${rows.length} 行`)
    } else {
      ElMessage.warning(errors[0] || '解析失败')
    }
  } catch {
    ElMessage.error('解析失败，请检查文件格式')
  } finally {
    parsing.value = false
  }
}

async function doImport() {
  if (!parsedRows.value.length) return
  importing.value = true
  try {
    await new Promise((r) => setTimeout(r, 400))
    emit('imported', [...parsedRows.value])
    ElMessage.success(`已导入 ${parsedRows.value.length} 条（前端示意，未落库）`)
    visible.value = false
  } finally {
    importing.value = false
  }
}

function reset() {
  fileList.value = []
  selectedFile.value = null
  parsing.value = false
  importing.value = false
  parsedRows.value = []
  parseErrors.value = []
}
</script>

<style scoped>
.import-tip {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.import-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.file-upload {
  display: inline-flex;
}

.file-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #606266;
}

.parse-errors {
  margin-bottom: 12px;
}

.parse-errors ul {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
  max-height: 88px;
  overflow: auto;
}

.preview-wrap {
  border: 1px solid #d0d7de;
  border-radius: 2px;
  overflow: hidden;
}

.preview-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  background: #d6ebf8;
  border-bottom: 1px solid #d0d7de;
}

.preview-count {
  margin-left: 8px;
  font-weight: 400;
  color: #606266;
}

.preview-wrap :deep(.el-table) {
  --el-table-header-bg-color: #eef6fc;
}
</style>
