import { ref } from 'vue'

type ElTableLike = {
  clearSelection?: () => void
  toggleRowSelection?: (row: Record<string, unknown>, selected?: boolean) => void
}

/** 跨页勾选：用 Set 缓存已选 id，配合 el-table row-key + reserve-selection */
export function useCrossPageSelection(rowKey = 'id') {
  const selectedIdSet = ref(new Set<string>())
  const selectedCount = ref(0)

  function syncFromTable(selection: Record<string, unknown>[]) {
    // selection-change 在 reserve-selection 下包含跨页已选行
    const next = new Set<string>()
    for (const row of selection) {
      const id = row[rowKey]
      if (id != null) next.add(String(id))
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

  function clearAll(tableRef: ElTableLike | null | undefined) {
    clear()
    tableRef?.clearSelection?.()
  }

  return {
    selectedIdSet,
    selectedCount,
    syncFromTable,
    selectedIds,
    clear,
    selectCurrentPage,
    clearAll
  }
}
