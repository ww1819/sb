import { ref } from 'vue'

/** 跨页勾选：用 Set 缓存已选 id，配合 el-table reserve-selection */
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

  return { selectedIdSet, selectedCount, syncFromTable, selectedIds, clear }
}
