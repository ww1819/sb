import QRCode from 'qrcode'
import http from '@/api/http'
import { ensureRefLabelMap, resolveRefLabel } from '@/composables/useRefLabelMap'

export interface AssetLabelPrintData {
  serialNumber?: string
  deviceName?: string
  specModel?: string
  recordDate?: string
  useDept?: string
  deviceCode?: string
  hospitalName?: string
}

function esc(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function formatDate(v: unknown): string {
  if (v === null || v === undefined || v === '') return ''
  const s = String(v)
  return s.length >= 10 ? s.slice(0, 10) : s
}

function formatSpecModel(row: Record<string, unknown>): string {
  const parts = [row.specification, row.model].filter((v) => v != null && String(v).trim() !== '')
  return parts.map((v) => String(v).trim()).join(' ')
}

async function resolveHospitalName(): Promise<string> {
  try {
    const { data } = await http.get('/system/campuses', { params: { limit: 1 } })
    const rows = data.data?.records ?? data.data ?? []
    const name = rows[0]?.campus_name
    if (name) return String(name)
  } catch {
    // ignore
  }
  return '医疗机构'
}

function buildLabelHtml(data: AssetLabelPrintData, qrDataUrl: string) {
  const hospital = esc(data.hospitalName || '医疗机构')
  const rows: [string, string][] = [
    ['序列号：', data.serialNumber || ''],
    ['资产名称：', data.deviceName || ''],
    ['规格型号：', data.specModel || ''],
    ['入账日期：', data.recordDate || ''],
    ['使用科室：', data.useDept || '']
  ]
  const fieldsHtml = rows
    .map(
      ([label, value]) =>
        `<div class="label-row"><span class="label-key">${esc(label)}</span><span class="label-val">${esc(value)}</span></div>`
    )
    .join('')

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>资产标签打印</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 24px;
      font-family: SimSun, "Songti SC", serif;
      color: #000;
      background: #fff;
    }
    .toolbar {
      margin-bottom: 16px;
    }
    .print-btn {
      border: 1px solid #333;
      background: #fff;
      color: #d03030;
      font-size: 16px;
      padding: 4px 18px;
      cursor: pointer;
      font-family: inherit;
    }
    .print-btn:hover {
      background: #fff5f5;
    }
    .asset-label {
      width: 520px;
      border: 1px solid #000;
      background: #fff;
    }
    .asset-label__title {
      text-align: center;
      font-size: 18px;
      font-weight: 700;
      padding: 10px 8px;
      border-bottom: 1px solid #000;
      letter-spacing: 1px;
    }
    .asset-label__body {
      display: flex;
      min-height: 188px;
    }
    .asset-label__fields {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }
    .label-row {
      display: flex;
      align-items: center;
      min-height: 37px;
      padding: 4px 10px;
      border-bottom: 1px solid #000;
      font-size: 15px;
      line-height: 1.4;
    }
    .label-row:last-child {
      border-bottom: none;
    }
    .label-key {
      flex-shrink: 0;
      white-space: nowrap;
    }
    .label-val {
      flex: 1;
      min-width: 0;
      word-break: break-all;
    }
    .asset-label__qr {
      width: 168px;
      border-left: 1px solid #000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 8px;
    }
    .asset-label__qr img {
      width: 148px;
      height: 148px;
      display: block;
    }
    @media print {
      body { padding: 0; }
      .toolbar { display: none; }
      .asset-label { width: 100%; max-width: 520px; }
    }
  </style>
</head>
<body>
  <div class="toolbar">
    <button type="button" class="print-btn" onclick="window.print()">打印</button>
  </div>
  <div class="asset-label">
    <div class="asset-label__title">${hospital}</div>
    <div class="asset-label__body">
      <div class="asset-label__fields">${fieldsHtml}</div>
      <div class="asset-label__qr">
        ${qrDataUrl ? `<img src="${qrDataUrl}" alt="二维码" />` : ''}
      </div>
    </div>
  </div>
</body>
</html>`
}

export async function printAssetLabelFromRow(row: Record<string, unknown>) {
  const deviceCode = String(row.device_code ?? '').trim()
  if (!deviceCode) {
    throw new Error('设备编码为空，无法打印标签')
  }

  await ensureRefLabelMap('department')
  const hospitalName = await resolveHospitalName()
  const useDept = resolveRefLabel('department', row.dept_id) || ''
  const recordDate = formatDate(row.enable_date ?? row.acceptance_date ?? row.purchase_date)

  let qrDataUrl = ''
  try {
    qrDataUrl = await QRCode.toDataURL(deviceCode, { width: 200, margin: 1 })
  } catch {
    qrDataUrl = ''
  }

  const html = buildLabelHtml(
    {
      hospitalName,
      serialNumber: String(row.serial_number ?? ''),
      deviceName: String(row.device_name ?? ''),
      specModel: formatSpecModel(row),
      recordDate,
      useDept,
      deviceCode
    },
    qrDataUrl
  )

  const win = window.open('', '_blank', 'width=640,height=720')
  if (!win) {
    throw new Error('请允许弹出窗口以打印标签')
  }
  win.document.write(html)
  win.document.close()
  win.focus()

  const deviceId = row.id
  if (deviceId) {
    try {
      await http.post(`/asset/device/${deviceId}/label/print`, { template_code: 'asset_sticker' })
    } catch {
      // 打印预览已打开，记录失败不阻断
    }
  }
}
