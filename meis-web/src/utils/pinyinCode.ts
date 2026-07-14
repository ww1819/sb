import { pinyin } from 'pinyin-pro'

/** 根据名称生成拼音简码（汉字取首字母，英文数字保留），与后端 PinyinCodeUtil 规则一致 */
export function toPinyinShortCode(text: string): string {
  if (!text?.trim()) return ''
  let result = ''
  for (const c of text.trim()) {
    if (c.charCodeAt(0) <= 127) {
      if (/[a-zA-Z0-9]/.test(c)) result += c.toLowerCase()
      continue
    }
    const py = pinyin(c, { pattern: 'first', toneType: 'none' })
    if (py) result += py.charAt(0).toLowerCase()
  }
  return result.toUpperCase()
}
