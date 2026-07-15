import { ElMessage, ElMessageBox } from 'element-plus'

/** 列表批量/导出作用域：勾选行 vs 当前查询全部结果 */
export type ListActionScope = 'selected' | 'all'

/**
 * 导出、批量变更前选择作用域。
 * - 未勾选：确认后走「全部查询结果」
 * - 已勾选：确认=全部查询结果，取消按钮=仅勾选行，关闭=中止
 */
export async function promptListActionScope(
  selectedCount: number,
  actionLabel: string
): Promise<ListActionScope | null> {
  if (selectedCount === 0) {
    try {
      await ElMessageBox.confirm(
        `当前未勾选任何行，将按当前查询条件对全部结果执行「${actionLabel}」。是否继续？`,
        actionLabel,
        { confirmButtonText: '全部查询结果', cancelButtonText: '取消', type: 'info' }
      )
      return 'all'
    } catch {
      return null
    }
  }

  try {
    await ElMessageBox.confirm(
      `已勾选 ${selectedCount} 条。请选择「${actionLabel}」范围。`,
      actionLabel,
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

export function assertScopeSelection(scope: ListActionScope, selectedCount: number): boolean {
  if (scope === 'selected' && selectedCount === 0) {
    ElMessage.warning('请先勾选要操作的行')
    return false
  }
  return true
}
