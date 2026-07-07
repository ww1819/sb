import http from '@/api/http'

export async function downloadApiFile(url: string, filename = 'download.csv') {
  const res = await http.get(url, { responseType: 'blob' })
  const blob = new Blob([res.data], { type: res.headers['content-type'] || 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  const disposition = res.headers['content-disposition'] as string | undefined
  const matched = disposition?.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
  link.download = matched ? decodeURIComponent(matched[1].replace(/"/g, '')) : filename
  link.click()
  URL.revokeObjectURL(link.href)
}
