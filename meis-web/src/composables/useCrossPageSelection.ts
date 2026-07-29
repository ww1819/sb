import { ref } from 'vue'

type ElTableLike = {
  clearSelection?: () => void
  toggleRowSelection?: (row: Record<string, unknown>, selected?: boolean) => void
}

/** 跨页勾选：用 Set 缓存已选 id，配合 el-table row-key + reserve-selection */
export function useCrossPageSelection(rowKey = 'id') {
  const selectedIdSet = ref(new Set<string>())
  const selectedCount = ref(0)

  /** 全量替换（来自「全选全部查询结果」） */
  function setSelectedIds(ids: string[]) {
    selectedIdSet.value = new Set(ids.map(String).filter(Boolean))
    selectedCount.value = selectedIdSet.value.size
  }

  /**
   * 按当页增量同步：只更新当前页 id 的勾选状态，保留其它页已缓存 id。
   * 支持「全选查询结果」后翻页仍保持 id 集合。
   */
  function syncFromTable(selection: Record<string, unknown>[], pageRows?: Record<string, unknown>[]) {
    if (!pageRows) {
      const next = new Set<string>()
      for (const row of selection) {
        const id = row[rowKey]
        if (id != null) next.add(String(id))
      }
      selectedIdSet.value = next
      selectedCount.value = next.size
      return
    }
    const pageIds = new Set(
      pageRows.map((r) => r[rowKey]).filter((id) => id != null).map((id) => String(id))
    )
    const selectedOnPage = new Set<string>()
    for (const row of selection) {
      const id = row[rowKey]
      if (id == null) continue
      const sid = String(id)
      if (pageIds.has(sid)) selectedOnPage.add(sid)
    }
    const next = new Set(selectedIdSet.value)
    for (const id of pageIds) {
      if (selectedOnPage.has(id)) next.add(id)
      else next.delete(id)
    }
    selectedIdSet.value = next
    selectedCount.value = next.size
  }

  function selectedIds(): string[] {
    return Array.from(selectedIdSet.value)
  }

  function clear() {
    selectedIdSet.value = new Set()
    selectedCount.value = 0
  }

  /** 全选当页：把当前页每一行设为选中（不清除其它页已选） */
  function selectCurrentPage(
    tableRef: ElTableLike | null | undefined,
    pageRows: Record<string, unknown>[]
  ) {
    if (!tableRef?.toggleRowSelection) return
    for (const row of pageRows) {
      tableRef.toggleRowSelection(row, true)
    }
  }

  /** 按缓存 id 回显当前页勾选 */
  function applyToCurrentPage(
    tableRef: ElTableLike | null | undefined,
    pageRows: Record<string, unknown>[]
  ) {
    if (!tableRef?.toggleRowSelection) return
    for (const row of pageRows) {
      const id = row[rowKey]
      if (id == null) continue
      tableRef.toggleRowSelection(row, selectedIdSet.value.has(String(id)))
    }
  }

  function clearAll(tableRef: ElTableLike | null | undefined) {
    clear()
    tableRef?.clearSelection?.()
  }

  return {
    selectedIdSet,
    selectedCount,
    syncFromTable,
    selectedIds,
    setSelectedIds,
    clear,
    selectCurrentPage,
    applyToCurrentPage,
    clearAll
  }
}
