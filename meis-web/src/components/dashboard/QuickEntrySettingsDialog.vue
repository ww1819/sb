<template>
  <el-dialog
    :model-value="modelValue"
    title="设置快捷入口"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="hint">仅可选择侧栏中有权限的菜单；最多 {{ maxCount }} 项。</p>
    <el-tree
      ref="treeRef"
      :data="treeData"
      node-key="id"
      show-checkbox
      :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
      :check-strictly="true"
      :default-expanded-keys="[]"
      @node-expand="onNodeExpand"
      @check="onCheck"
    />
    <template #footer>
      <span class="selected-count">已选 {{ checkedPaths.length }} / {{ maxCount }}</span>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ElTree } from 'element-plus'
import http from '@/api/http'
import type { NavModule } from '@/utils/menuNav'

export interface QuickEntryTreeNode {
  id: string
  label: string
  path?: string
  disabled?: boolean
  children?: QuickEntryTreeNode[]
}

type TreeElNode = {
  expanded?: boolean
  parent?: { childNodes?: TreeElNode[] }
  childNodes?: TreeElNode[]
}

const props = defineProps<{
  modelValue: boolean
  modules: NavModule[]
  selectedPaths: string[]
  maxCount?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [paths: string[]]
}>()

const maxCount = props.maxCount ?? 45
const treeRef = ref<InstanceType<typeof ElTree>>()
const treeData = ref<QuickEntryTreeNode[]>([])
const checkedPaths = ref<string[]>([])
const saving = ref(false)

function buildTree(modules: NavModule[]): QuickEntryTreeNode[] {
  return modules
    .map((mod) => {
      const children: QuickEntryTreeNode[] = []
      if (mod.path && !(mod.groups?.length)) {
        children.push({
          id: mod.path,
          label: mod.title,
          path: mod.path
        })
      }
      for (const group of mod.groups ?? []) {
        const leafs: QuickEntryTreeNode[] = (group.items ?? [])
          .filter((i) => i.path)
          .map((i) => ({
            id: i.path,
            label: i.title,
            path: i.path
          }))
        if (group.groups?.length) {
          for (const sub of group.groups) {
            for (const i of sub.items ?? []) {
              if (!i.path) continue
              leafs.push({ id: i.path, label: i.title, path: i.path })
            }
          }
        }
        if (!leafs.length) continue
        if (group.title) {
          children.push({
            id: `group:${mod.id}:${group.id ?? group.title}`,
            label: group.title,
            disabled: true,
            children: leafs
          })
        } else {
          children.push(...leafs)
        }
      }
      if (!children.length) return null
      return {
        id: `mod:${mod.id}`,
        label: mod.title,
        disabled: true,
        children
      } as QuickEntryTreeNode
    })
    .filter((n): n is QuickEntryTreeNode => !!n)
}

function collectLeafPaths(nodes: QuickEntryTreeNode[]): string[] {
  const out: string[] = []
  for (const n of nodes) {
    if (n.path) out.push(n.path)
    if (n.children?.length) out.push(...collectLeafPaths(n.children))
  }
  return out
}

/** 同级手风琴（与侧栏/分类树一致）：展开一个时收起同级其它已展开兄弟 */
function onNodeExpand(_data: QuickEntryTreeNode, node: TreeElNode) {
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

function onCheck() {
  const tree = treeRef.value
  if (!tree) return
  const nodes = tree.getCheckedNodes(false, false) as QuickEntryTreeNode[]
  const keys = nodes.filter((n) => !!n.path).map((n) => n.path!)
  if (keys.length > maxCount) {
    ElMessage.warning(`最多选择 ${maxCount} 项`)
    tree.setCheckedKeys(checkedPaths.value)
    return
  }
  checkedPaths.value = keys
}

async function save() {
  saving.value = true
  try {
    const { data } = await http.put('/system/users/me/preferences/quick-entry', {
      paths: checkedPaths.value
    })
    if (data.code !== 0 && data.code !== 200) {
      throw new Error(data.message || '保存失败')
    }
    const paths = (data.data?.quickEntryPaths as string[]) ?? checkedPaths.value
    ElMessage.success('已保存快捷入口')
    emit('saved', paths)
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    treeData.value = buildTree(props.modules)
    const allowed = new Set(collectLeafPaths(treeData.value))
    checkedPaths.value = props.selectedPaths.filter((p) => allowed.has(p)).slice(0, maxCount)
    await nextTick()
    treeRef.value?.setCheckedKeys(checkedPaths.value)
  }
)
</script>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.selected-count {
  float: left;
  line-height: 32px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

:deep(.el-tree-node__content) {
  height: 32px;
}
</style>
