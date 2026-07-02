<template>
  <div class="permission-editor">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="菜单权限" name="menus">
        <p class="permission-hint">勾选用户可访问的菜单与页面；全选表示拥有全部菜单权限。</p>
        <el-checkbox v-model="allMenus" @change="toggleAllMenus">全选菜单</el-checkbox>
        <div class="permission-tree-box">
          <el-tree
            ref="menuTreeRef"
            :data="menuTree"
            show-checkbox
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            default-expand-all
            @check="emitChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="科室权限" name="depts">
        <p class="permission-hint">限制用户可操作的科室范围，用于数据隔离与业务单据归属。</p>
        <div class="permission-tree-box">
          <el-tree
            ref="deptTreeRef"
            :data="deptTree"
            show-checkbox
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            default-expand-all
            @check="emitChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="库房权限" name="warehouses">
        <p class="permission-hint">勾选用户可管理的库房，未勾选则无法操作对应库房业务。</p>
        <el-checkbox-group v-model="model.warehouseIds" @change="emitChange">
          <el-row :gutter="12">
            <el-col v-for="w in warehouses" :key="w.id" :span="12" :lg="8">
              <el-checkbox :label="w.id" class="grid-checkbox">
                {{ w.warehouse_name }} ({{ w.warehouse_code }})
              </el-checkbox>
            </el-col>
          </el-row>
        </el-checkbox-group>
        <el-empty v-if="!warehouses.length" description="暂无库房数据" :image-size="64" />
      </el-tab-pane>

      <el-tab-pane label="按钮权限" name="buttons">
        <p class="permission-hint">控制页面内增删改查、导出等按钮是否可见；全选表示拥有全部按钮权限。</p>
        <el-checkbox v-model="allButtons" @change="toggleAllButtons">全部按钮</el-checkbox>
        <el-checkbox-group v-model="model.buttons" class="button-group" @change="onButtonsChange">
          <el-row :gutter="12">
            <el-col v-for="b in buttonOptions" :key="b.code" :span="12" :lg="8">
              <el-checkbox :label="b.code" class="grid-checkbox">{{ b.label }}</el-checkbox>
            </el-col>
          </el-row>
        </el-checkbox-group>
      </el-tab-pane>

      <el-tab-pane label="数据范围" name="scope">
        <p class="permission-hint">定义列表与报表的数据可见范围；自定义时可勾选具体科室。</p>
        <el-radio-group v-model="model.dataScope" @change="emitChange">
          <el-radio value="all">全部数据</el-radio>
          <el-radio value="dept">本科室</el-radio>
          <el-radio value="self">仅本人</el-radio>
          <el-radio value="custom">自定义科室</el-radio>
        </el-radio-group>
        <div v-if="model.dataScope === 'custom'" class="permission-tree-box" style="margin-top:12px">
          <el-tree
            ref="scopeDeptTreeRef"
            :data="deptTree"
            show-checkbox
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            default-expand-all
            @check="emitChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="permission-summary">
      已选：菜单 {{ menuSummary }} · 按钮 {{ buttonSummary }} · 库房 {{ model.warehouseIds.length }} 个 · 数据范围 {{ scopeLabel }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import http from '@/api/http'

export interface PermissionModel {
  menus: string[]
  buttons: string[]
  dataScope: string
  deptIds: string[]
  warehouseIds: string[]
}

const props = defineProps<{ value?: PermissionModel }>()
const emit = defineEmits<{ change: [v: PermissionModel] }>()

const activeTab = ref('menus')
const menuTree = ref<any[]>([])
const deptTree = ref<any[]>([])
const warehouses = ref<any[]>([])
const buttonOptions = ref<{ code: string; label: string }[]>([])
const menuTreeRef = ref()
const deptTreeRef = ref()
const scopeDeptTreeRef = ref()
const allMenus = ref(false)
const allButtons = ref(false)

const model = reactive<PermissionModel>({
  menus: [],
  buttons: [],
  dataScope: 'self',
  deptIds: [],
  warehouseIds: []
})

const scopeLabels: Record<string, string> = {
  all: '全部数据',
  dept: '本科室',
  self: '仅本人',
  custom: '自定义科室'
}

const scopeLabel = computed(() => scopeLabels[model.dataScope] || model.dataScope)

const menuSummary = computed(() => {
  if (allMenus.value) return '全部'
  const checked = menuTreeRef.value?.getCheckedKeys(false)?.length || 0
  const half = menuTreeRef.value?.getHalfCheckedKeys()?.length || 0
  return checked + half > 0 ? `${checked + half} 项` : '0 项'
})

const buttonSummary = computed(() => {
  if (allButtons.value) return '全部'
  return model.buttons.length ? `${model.buttons.length} 项` : '0 项'
})

onMounted(async () => {
  const [menus, depts, wh, buttons] = await Promise.all([
    http.get('/system/menus/permission-tree'),
    http.get('/system/org/dept-tree'),
    http.get('/system/warehouses'),
    http.get('/system/permission/buttons')
  ])
  if (menus.data.code === 0) menuTree.value = menus.data.data
  if (depts.data.code === 0) deptTree.value = depts.data.data
  if (wh.data.code === 0) warehouses.value = wh.data.data
  if (buttons.data.code === 0) buttonOptions.value = buttons.data.data
  applyValue(props.value)
})

watch(() => props.value, applyValue, { deep: true })

function applyValue(v?: PermissionModel) {
  if (!v) return
  model.menus = [...(v.menus || [])]
  model.buttons = [...(v.buttons || [])]
  model.dataScope = v.dataScope || 'self'
  model.deptIds = [...(v.deptIds || [])]
  model.warehouseIds = [...(v.warehouseIds || [])]
  allMenus.value = model.menus.includes('*')
  allButtons.value = model.buttons.includes('*')
  setTimeout(() => {
    if (menuTreeRef.value && !allMenus.value) menuTreeRef.value.setCheckedKeys(leafMenuKeys(model.menus))
    if (deptTreeRef.value) deptTreeRef.value.setCheckedKeys(model.deptIds)
    if (scopeDeptTreeRef.value && model.dataScope === 'custom') scopeDeptTreeRef.value.setCheckedKeys(model.deptIds)
  }, 100)
}

function leafMenuKeys(codes: string[]) {
  if (codes.includes('*')) return collectAllMenuIds(menuTree.value)
  return codes
}

function collectAllMenuIds(nodes: any[]): string[] {
  const ids: string[] = []
  for (const n of nodes) {
    ids.push(n.id)
    if (n.children?.length) ids.push(...collectAllMenuIds(n.children))
  }
  return ids
}

function toggleAllMenus(v: boolean) {
  if (v) {
    model.menus = ['*']
    menuTreeRef.value?.setCheckedKeys(collectAllMenuIds(menuTree.value))
  } else {
    model.menus = []
    menuTreeRef.value?.setCheckedKeys([])
  }
  emitChange()
}

function toggleAllButtons(v: boolean) {
  model.buttons = v ? ['*'] : []
  emitChange()
}

function onButtonsChange() {
  allButtons.value = false
  emitChange()
}

function collectCheckedMenus(): string[] {
  if (allMenus.value) return ['*']
  const checked = menuTreeRef.value?.getCheckedKeys(false) || []
  const half = menuTreeRef.value?.getHalfCheckedKeys() || []
  return [...new Set([...checked, ...half])]
}

function collectCheckedDepts(): string[] {
  return deptTreeRef.value?.getCheckedKeys(false) || []
}

function collectScopeDepts(): string[] {
  if (model.dataScope !== 'custom') return []
  return scopeDeptTreeRef.value?.getCheckedKeys(false) || []
}

function buildModel(): PermissionModel {
  const menus = collectCheckedMenus()
  const deptIds = model.dataScope === 'custom' ? collectScopeDepts() : collectCheckedDepts()
  return {
    menus: allMenus.value ? ['*'] : menus,
    buttons: allButtons.value ? ['*'] : [...model.buttons],
    dataScope: model.dataScope,
    deptIds,
    warehouseIds: [...model.warehouseIds]
  }
}

function emitChange() {
  emit('change', buildModel())
}

defineExpose({ getPermissions: buildModel, emitChange })
</script>

<style scoped>
.permission-editor {
  padding: 4px 0;
}

.button-group {
  margin-top: 12px;
}

.grid-checkbox {
  display: flex;
  margin-bottom: 8px;
  white-space: normal;
  height: auto;
  line-height: 1.5;
}
</style>
