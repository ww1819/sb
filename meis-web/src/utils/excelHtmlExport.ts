/** 前端示意报表：导出为 Excel 可打开的 HTML/.xls */

export function formatExportDate(d = new Date()) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

export function escapeHtml(s: string) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function downloadExcelHtml(htmlTable: string, filenameWithoutExt: string) {
  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8" /></head><body>${htmlTable}</body></html>`
  const blob = new Blob([`\uFEFF${html}`], { type: 'application/vnd.ms-excel;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filenameWithoutExt}.xls`
  a.click()
  URL.revokeObjectURL(url)
}

export function parseMoney(s: string) {
  return Number(String(s).replace(/,/g, '')) || 0
}
