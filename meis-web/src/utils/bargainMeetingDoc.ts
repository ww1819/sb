/** 设备购置询价议价会议记录：打印 / Word 下载（版式对齐页面 A4 表单） */

export interface BargainMeetingDoc {
  meetingLocation?: string
  meetingTime?: string
  deptName?: string
  deviceName?: string
  participantDepts?: string
  deptOpinion?: string
  meetingContent?: string
  meetingConclusion?: string
}

function esc(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 空内容也保留空白，避免打印/Word 把大框压扁 */
function boxHtml(v: unknown, heightPx: number) {
  const raw = v == null ? '' : String(v).trim()
  const text = raw ? esc(raw).replace(/\n/g, '<br/>') : '&nbsp;'
  return `<div class="box" style="height:${heightPx}px;min-height:${heightPx}px;line-height:1.6;">${text}</div>`
}

function valHtml(v: unknown) {
  const raw = v == null ? '' : String(v).trim()
  return raw ? esc(raw) : '&nbsp;'
}

/**
 * 生成与页面「设备购置询价议价会议记录」一致的 A4 表格 HTML。
 * forWord：加入 Word 兼容页边距与固定行高（Word 对 min-height 支持差）。
 */
function buildMeetingHtml(doc: BargainMeetingDoc, mode: 'print' | 'word') {
  const title = '设备购置询价议价会议记录'
  const isWord = mode === 'word'
  // 与页面 textarea 视觉高度接近：意见≈6行、内容/结论≈8行
  const hOpinion = 130
  const hContent = 170
  const hConclusion = 170

  const wordHead = isWord
    ? `<!--[if gte mso 9]><xml>
<w:WordDocument>
  <w:View>Print</w:View>
  <w:Zoom>100</w:Zoom>
  <w:DoNotOptimizeForBrowser/>
</w:WordDocument>
</xml><![endif]-->`
    : ''

  return `<!DOCTYPE html>
<html xmlns:o="urn:schemas-microsoft-com:office:office"
      xmlns:w="urn:schemas-microsoft-com:office:word"
      xmlns="http://www.w3.org/TR/REC-html40">
<head>
<meta charset="utf-8"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<title>${esc(title)}</title>
${wordHead}
<style>
  /* A4：210mm x 297mm */
  @page {
    size: A4 portrait;
    margin: 18mm 16mm 18mm 16mm;
  }
  ${
    isWord
      ? `
  @page Section1 {
    size: 595.3pt 841.9pt;
    margin: 56.7pt 50.4pt 56.7pt 50.4pt;
  }
  div.Section1 { page: Section1; }
  `
      : ''
  }
  html, body {
    margin: 0;
    padding: 0;
    background: #fff;
    color: #111;
    font-family: "SimSun", "宋体", "Songti SC", serif;
    font-size: 14pt;
  }
  .sheet {
    width: 100%;
    box-sizing: border-box;
    ${isWord ? '' : 'min-height: 261mm;'}
    padding: ${isWord ? '0' : '4mm 2mm'};
  }
  .title {
    text-align: center;
    font-size: 18pt;
    font-weight: bold;
    letter-spacing: 3px;
    margin: 0 0 14pt;
    line-height: 1.4;
  }
  table.doc {
    width: 100%;
    border-collapse: collapse;
    table-layout: fixed;
    border: 1.5pt solid #111;
  }
  table.doc th,
  table.doc td {
    border: 1pt solid #222;
    padding: 8pt 10pt;
    vertical-align: middle;
    font-size: 12pt;
    line-height: 1.5;
    color: #111;
  }
  table.doc th {
    width: 92pt;
    background: #f5f5f5;
    font-weight: bold;
    text-align: center;
    white-space: nowrap;
  }
  table.doc td.val {
    word-break: break-word;
  }
  table.doc tr.tall th,
  table.doc tr.tall td {
    vertical-align: top;
  }
  .box {
    width: 100%;
    box-sizing: border-box;
    overflow: hidden;
    word-break: break-word;
  }
  @media print {
    body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    table.doc th { background: #f5f5f5 !important; }
  }
</style>
</head>
<body>
<div class="Section1 sheet">
  <div class="title">${esc(title)}</div>
  <table class="doc" cellspacing="0" cellpadding="0">
    <colgroup>
      <col style="width:92pt"/>
      <col style="width:45%"/>
      <col style="width:92pt"/>
      <col style="width:45%"/>
    </colgroup>
    <tr style="height:36pt">
      <th>会议地点</th>
      <td class="val">${valHtml(doc.meetingLocation)}</td>
      <th>会议时间</th>
      <td class="val">${valHtml(doc.meetingTime)}</td>
    </tr>
    <tr style="height:36pt">
      <th>申请科室</th>
      <td class="val">${valHtml(doc.deptName)}</td>
      <th>设备名称</th>
      <td class="val">${valHtml(doc.deviceName)}</td>
    </tr>
    <tr style="height:36pt">
      <th>参与部门</th>
      <td class="val" colspan="3">${valHtml(doc.participantDepts)}</td>
    </tr>
    <tr class="tall" style="height:${hOpinion}px">
      <th>设备科意见</th>
      <td class="val" colspan="3" height="${hOpinion}">${boxHtml(doc.deptOpinion, hOpinion)}</td>
    </tr>
    <tr class="tall" style="height:${hContent}px">
      <th>会议内容</th>
      <td class="val" colspan="3" height="${hContent}">${boxHtml(doc.meetingContent, hContent)}</td>
    </tr>
    <tr class="tall" style="height:${hConclusion}px">
      <th>会议结论</th>
      <td class="val" colspan="3" height="${hConclusion}">${boxHtml(doc.meetingConclusion, hConclusion)}</td>
    </tr>
  </table>
</div>
${mode === 'print' ? '<script>window.onload=function(){setTimeout(function(){window.print()},80)}<\/script>' : ''}
</body>
</html>`
}

/** 连接打印机：按页面 A4 表单版式打印 */
export function printBargainMeetingDoc(doc: BargainMeetingDoc) {
  const html = buildMeetingHtml(doc, 'print')
  const w = window.open('', '_blank')
  if (!w) return false
  w.document.open()
  w.document.write(html)
  w.document.close()
  return true
}

/** 下载 Word（.doc）：版式与页面会议记录表一致 */
export function downloadBargainMeetingDoc(doc: BargainMeetingDoc, filename?: string) {
  const html = buildMeetingHtml(doc, 'word')
  const blob = new Blob(['\ufeff' + html], {
    type: 'application/msword;charset=utf-8'
  })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename || `设备购置询价议价会议记录表.doc`
  a.click()
  URL.revokeObjectURL(a.href)
}
