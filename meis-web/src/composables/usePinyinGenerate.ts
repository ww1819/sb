import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

export type PinyinGenerateScope = 'all' | 'selected'

/** 弹窗选择生成范围后调用后端接口 */
export async function promptPinyinScope(selectedCount: number): Promise<PinyinGenerateScope | null> {
  if (selectedCount === 0) {
    try {
      await ElMessageBox.confirm(
        '当前未勾选任何行，将按当前查询条件更新全部结果。是否继续？',
        '生成拼音简码',
        { confirmButtonText: '更新全部结果', cancelButtonText: '取消', type: 'info' }
      )
      return 'all'
    } catch {
      return null
    }
  }

  try {
    await ElMessageBox.confirm(
      `已勾选 ${selectedCount} 条记录。请选择拼音简码的更新范围。`,
      '生成拼音简码',
      {
        confirmButtonText: '全部查询结果',
        cancelButtonText: '仅勾选行',
        distinguishCancelAndClose: true,
        type: 'info'
      }
    )
    return 'all'
  } catch (action) {
    if (action === 'cancel') return 'selected'
    return null
  }
}

export async function executePinyinGenerate(
  apiUrl: string,
  scope: PinyinGenerateScope,
  options: { selectedIds: string[]; keyword?: string }
) {
  if (scope === 'selected' && options.selectedIds.length === 0) {
    ElMessage.warning('请先勾选要更新的行')
    return false
  }
  const body: Record<string, unknown> =
    scope === 'all'
      ? { all: true, keyword: options.keyword || undefined }
      : { ids: options.selectedIds }
  const { data } = await http.post(apiUrl, body)
  ElMessage.success(`已更新 ${data.data?.updated ?? 0} 条拼音简码`)
  return true
}
