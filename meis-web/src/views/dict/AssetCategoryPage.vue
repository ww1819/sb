<template>
  <div class="asset-category-page">
    <aside class="tree-pane">
      <div class="tree-pane__header">资产分类树</div>
      <el-input
        v-model="treeKeyword"
        clearable
        placeholder="筛选分类"
        class="tree-pane__search"
      />
      <el-tree
        ref="treeRef"
        v-loading="treeLoading"
        class="tree-pane__tree"
        node-key="id"
        :data="treeData"
        :props="{ label: 'label', children: 'children' }"
        :filter-node-method="filterNode"
        :default-expanded-keys="[ALL_ID]"
        highlight-current
        @node-click="onNodeClick"
        @node-expand="onNodeExpand"
      />
    </aside>
    <div class="list-pane">
      <CrudPage
        :config="config"
        :extra-query="extraQuery"
        :default-form-values="defaultFormValues"
        @saved="loadTree"
        @deleted="loadTree"
        @imported="loadTree"
      >
        <template #form="{ form, fields, mode }">
          <el-form label-width="120px" :disabled="mode === 'view'">
            <GroupedFormFields :table="config.table" :model="form" :fields="fields">
              <template #field-parent_id="{ field, model }">
                <RefSelect
                  v-model="model[field.prop]"
                  :link-table="field.linkTable!"
                  placeholder="留空为一级分类"
                  :exclude-values="parentExcludeIds(model)"
                />
              </template>
            </GroupedFormFields>
          </el-form>
        </template>
      </CrudPage>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { ElTree } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import { getPageConfig } from '@/config/pageRegistry'

interface TreeNode {
  id: string
  label: string
  parentId: string | null
  children?: TreeNode[]
}

type TreeElNode = {
  expanded?: boolean
  parent?: { childNodes?: TreeElNode[] }
  childNodes?: TreeElNode[]
}

const ALL_ID = '__all__'

const route = useRoute()
const path = computed(() => '/' + (route.params.module as string) + '/' + (route.params.page as string))
const config = computed(() => getPageConfig(path.value)!)

const treeRef = ref<InstanceType<typeof ElTree>>()
const treeLoading = ref(false)
const treeKeyword = ref('')
const flatRows = ref<Record<string, unknown>[]>([])
const selectedId = ref<string>(ALL_ID)
const idSet = computed(() => new Set(flatRows.value.map((r) => String(r.id))))

const treeData = computed<TreeNode[]>(() => {
  const root: TreeNode = { id: ALL_ID, label: '全部', parentId: null, children: buildTree(null) }
  return [root]
})

const extraQuery = computed(() => {
  if (!selectedId.value || selectedId.value === ALL_ID) return {}
  return { tree_node_id: selectedId.value }
})

const defaultFormValues = computed(() => {
  if (!selectedId.value || selectedId.value === ALL_ID) return {}
  return { parent_id: selectedId.value }
})

watch(treeKeyword, (val) => {
  treeRef.value?.filter(val)
})

onMounted(async () => {
  await loadTree()
  treeRef.value?.setCurrentKey(ALL_ID)
})

async function loadTree() {
  treeLoading.value = true
  try {
    const { data } = await http.get('/system/asset_category/list', { params: { limit: 5000 } })
    if (data.code === 0) flatRows.value = data.data ?? []
  } finally {
    treeLoading.value = false
  }
}

/** 自指、指到不存在节点 → 视为一级 */
function effectiveParentId(row: Record<string, unknown>): string | null {
  const id = String(row.id)
  const raw = row.parent_id
  if (raw == null || raw === '') return null
  const pid = String(raw)
  if (pid === id || !idSet.value.has(pid)) return null
  return pid
}

function buildTree(parentId: string | null): TreeNode[] {
  return flatRows.value
    .filter((r) => effectiveParentId(r) === parentId)
    .sort((a, b) => Number(a.sort_order ?? 0) - Number(b.sort_order ?? 0))
    .map((r) => {
      const id = String(r.id)
      const children = buildTree(id)
      const node: TreeNode = {
        id,
        label: `${r.category_code ?? ''} ${r.category_name ?? ''}`.trim(),
        parentId
      }
      if (children.length) node.children = children
      return node
    })
}

function filterNode(value: string, data: TreeNode) {
  if (!value) return true
  return data.label.toLowerCase().includes(value.trim().toLowerCase())
}

function onNodeClick(data: TreeNode) {
  selectedId.value = data.id
}

/** 同级手风琴：展开一个时收起同级其它已展开兄弟 */
function onNodeExpand(_data: TreeNode, node: TreeElNode) {
  const siblings = node.parent?.childNodes
  if (!siblings?.length) return
  for (const sibling of siblings) {
    if (sibling === node) continue
    if (sibling.expanded) collapseTreeNode(sibling)
  }
}

function collapseTreeNode(node: TreeElNode) {
  node.expanded = false
  for (const child of node.childNodes ?? []) {
    collapseTreeNode(child)
  }
}

/** 编辑时不可选自身及子孙，防止成环；清空后为一级分类 */
function parentExcludeIds(model: Record<string, unknown>): string[] {
  const id = model.id != null && model.id !== '' ? String(model.id) : ''
  if (!id) return []
  const ban = new Set<string>([id])
  let frontier = [id]
  while (frontier.length) {
    const next: string[] = []
    for (const row of flatRows.value) {
      const rid = String(row.id)
      const pid = effectiveParentId(row)
      if (pid && frontier.includes(pid) && !ban.has(rid)) {
        ban.add(rid)
        next.push(rid)
      }
    }
    frontier = next
  }
  return [...ban]
}
</script>

<style scoped>
.asset-category-page {
  display: flex;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.tree-pane {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  box-shadow: var(--meis-card-shadow);
  overflow: hidden;
}

.tree-pane__header {
  flex-shrink: 0;
  padding: 12px 14px;
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
  border-bottom: 1px solid var(--meis-border-light);
}

.tree-pane__search {
  flex-shrink: 0;
  padding: 10px 12px 0;
}

.tree-pane__tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px 8px 12px;
}

.list-pane {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
</style>
