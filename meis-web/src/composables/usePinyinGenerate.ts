import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { promptListActionScope, type ListActionScope } from '@/composables/useListActionScope'

export type PinyinGenerateScope = ListActionScope

/** 弹窗选择生成范围后调用后端接口（与列表导出/批量作用域约定一致） */
export async function promptPinyinScope(selectedCount: number): Promise<PinyinGenerateScope | null> {
  return promptListActionScope(selectedCount, '生成拼音简码')
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
