<template>
  <div class="device-category-page">
    <aside class="tree-pane">
      <div class="tree-pane__header">设备分类树</div>
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
        highlight-current
        default-expand-all
        @node-click="onNodeClick"
      />
    </aside>
    <div class="list-pane">
      <CrudPage
        :config="config"
        :extra-query="extraQuery"
        :default-form-values="defaultFormValues"
        @saved="loadTree"
        @deleted="loadTree"
      >
        <template #form="{ form, fields, mode }">
          <el-form label-width="130px" :disabled="mode === 'view'">
            <GroupedFormFields :table="config.table" :model="form" :fields="fields">
              <template #field-parent_code="{ field, model }">
                <RefSelect
                  v-model="model[field.prop]"
                  link-table="medical_device_category"
                  value-key="category_code"
                  placeholder="留空为一级分类"
                  :exclude-values="parentExcludeCodes(model)"
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
  code: string
  label: string
  parentCode: string | null
  children?: TreeNode[]
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

const codeSet = computed(() => new Set(flatRows.value.map((r) => String(r.category_code ?? '')).filter(Boolean)))

const selectedRow = computed(() =>
  selectedId.value === ALL_ID ? null : flatRows.value.find((r) => String(r.id) === selectedId.value) ?? null
)

const treeData = computed<TreeNode[]>(() => {
  const root: TreeNode = {
    id: ALL_ID,
    code: '',
    label: '全部',
    parentCode: null,
    children: buildTree(null)
  }
  return [root]
})

const extraQuery = computed(() => {
  if (!selectedId.value || selectedId.value === ALL_ID) return {}
  return { tree_node_id: selectedId.value }
})

const defaultFormValues = computed(() => {
  const row = selectedRow.value
  if (!row) return { level: 1 }
  return {
    parent_code: String(row.category_code ?? ''),
    level: Number(row.level ?? 0) + 1
  }
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
    const { data } = await http.get('/system/medical_device_category/list', { params: { limit: 500 } })
    if (data.code === 0) flatRows.value = data.data ?? []
  } finally {
    treeLoading.value = false
  }
}

/** 空/自指/指到不存在编码 → 视为一级 */
function effectiveParentCode(row: Record<string, unknown>): string | null {
  const code = String(row.category_code ?? '')
  const raw = row.parent_code
  if (raw == null || raw === '') return null
  const pc = String(raw)
  if (!pc || pc === code || !codeSet.value.has(pc)) return null
  return pc
}

function buildTree(parentCode: string | null): TreeNode[] {
  return flatRows.value
    .filter((r) => effectiveParentCode(r) === parentCode)
    .sort((a, b) => Number(a.sort_order ?? 0) - Number(b.sort_order ?? 0))
    .map((r) => {
      const id = String(r.id)
      const code = String(r.category_code ?? '')
      const children = buildTree(code)
      const node: TreeNode = {
        id,
        code,
        label: `${code} ${r.category_name ?? ''}`.trim(),
        parentCode
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

/** 不可选自身及子孙编码，防止成环；清空上级=一级分类 */
function parentExcludeCodes(model: Record<string, unknown>): string[] {
  const code = model.category_code != null && model.category_code !== '' ? String(model.category_code) : ''
  if (!code) return []
  const ban = new Set<string>([code])
  let frontier = [code]
  while (frontier.length) {
    const next: string[] = []
    for (const row of flatRows.value) {
      const c = String(row.category_code ?? '')
      const pc = effectiveParentCode(row)
      if (pc && frontier.includes(pc) && !ban.has(c)) {
        ban.add(c)
        next.push(c)
      }
    }
    frontier = next
  }
  return [...ban]
}
</script>

<style scoped>
.device-category-page {
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
