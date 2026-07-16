<template>
  <div class="grouped-form">
    <template v-if="groups.length">
      <FormSection v-for="g in groups" :key="g.group" :title="sectionTitle(g.group)">
        <template v-if="groupPanel(g.group)">
          <div
            v-if="groupColumns(g.group) && panelOuterFieldRows(g).length"
            class="form-grid-stack"
          >
            <div
              v-for="(row, rowIdx) in panelOuterFieldRows(g)"
              :key="rowIdx"
              class="form-grid"
              :class="formGridClass(g.group)"
              :style="panelOuterGridStyle(g.group)"
            >
              <el-form-item
                v-for="f in row"
                :key="f.prop"
                :label="f.label"
                :required="f.required"
                :class="formItemClass(f.prop)"
                :style="gridItemStyle(f, g.group)"
              >
                <slot :name="`field-${f.prop}`" :field="f" :model="model">
                  <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
                </slot>
              </el-form-item>
            </div>
          </div>
          <div
            v-else-if="panelOuterFields(g).length"
            class="form-grid"
            :class="formGridClass(g.group)"
            :style="panelOuterGridStyle(g.group)"
          >
            <el-form-item
              v-for="f in panelOuterFields(g)"
              :key="f.prop"
              :label="f.label"
              :required="f.required"
              :class="formItemClass(f.prop)"
              :style="gridItemStyle(f, g.group)"
            >
              <slot :name="`field-${f.prop}`" :field="f" :model="model">
                <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
              </slot>
            </el-form-item>
          </div>
          <div v-if="panelInnerFields(g).length" class="form-group-panel">
            <div class="form-group-panel__row">
              <el-form-item
                v-for="f in panelInnerFields(g)"
                :key="f.prop"
                :label="f.label"
                :required="f.required"
                :class="['form-group-panel__item', formItemClass(f.prop)]"
              >
                <slot :name="`field-${f.prop}`" :field="f" :model="model">
                  <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
                </slot>
              </el-form-item>
            </div>
          </div>
        </template>
        <template v-else>
        <div v-if="groupColumns(g.group) && groupFieldRows(g).length" class="form-grid-stack">
          <div
            v-for="(row, rowIdx) in groupFieldRows(g)"
            :key="rowIdx"
            class="form-grid"
            :class="formGridClass(g.group)"
            :style="{ gridTemplateColumns: `repeat(${groupColumns(g.group)}, minmax(0, 1fr))` }"
          >
          <el-form-item
            v-for="f in row"
            :key="f.prop"
            :label="f.label"
            :required="f.required"
            :class="formItemClass(f.prop)"
            :style="gridItemStyle(f, g.group)"
          >
              <slot :name="`field-${f.prop}`" :field="f" :model="model">
                <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
              </slot>
            </el-form-item>
          </div>
        </div>
        <div
          v-else-if="groupColumns(g.group)"
          class="form-grid"
          :class="formGridClass(g.group)"
          :style="{ gridTemplateColumns: `repeat(${groupColumns(g.group)}, minmax(0, 1fr))` }"
        >
          <el-form-item
            v-for="f in g.fields"
            :key="f.prop"
            :label="f.label"
            :required="f.required"
            :class="formItemClass(f.prop)"
            :style="gridItemStyle(f, g.group)"
          >
            <slot :name="`field-${f.prop}`" :field="f" :model="model">
              <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
            </slot>
          </el-form-item>
        </div>
        <el-row v-else :gutter="16">
          <el-col v-for="f in g.fields" :key="f.prop" :span="fieldSpan(f, g.group)">
            <el-form-item :label="f.label" :required="f.required" :class="formItemClass(f.prop)">
              <slot :name="`field-${f.prop}`" :field="f" :model="model">
                <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
              </slot>
            </el-form-item>
          </el-col>
        </el-row>
        </template>
      </FormSection>
    </template>
    <template v-else>
      <el-form-item v-for="f in flatFields" :key="f.prop" :label="f.label" :required="f.required">
                <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import FormSection from '@/components/form/FormSection.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import { getGroupedFields, getSchema, groupTitle, type FieldGroup, type FieldSchema } from '@/config/pageSchemas'

function buildGroups(schema: FieldSchema[]) {
  const groups = new Map<string, FieldSchema[]>()
  for (const f of schema) {
    const g = f.group ?? 'other'
    if (!groups.has(g)) groups.set(g, [])
    groups.get(g)!.push(f)
  }
  const order = ['basic', 'finance', 'location', 'vendor', 'time', 'accounting', 'status', 'workflow', 'approval', 'compliance', 'other', 'attachment', 'remark']
  return order.filter((g) => groups.has(g)).map((g) => ({ group: g as FieldSchema['group'], fields: groups.get(g)! }))
}

const props = defineProps<{
  table: string
  model: Record<string, unknown>
  fields?: FieldSchema[]
  groupSpan?: Partial<Record<FieldGroup, number>>
  /** 指定分组每行显示的列数（使用 CSS Grid，适用于 5 列等非 24 栅格整除布局） */
  groupColumns?: Partial<Record<FieldGroup, number>>
  /** 覆盖分组标题 */
  groupTitles?: Partial<Record<FieldGroup, string>>
  /** 指定分组内字段分行（每行一组 prop，配合 groupColumns 使用） */
  groupRows?: Partial<Record<FieldGroup, string[][]>>
  /** 分组内嵌面板：outer 为面板外字段，inner 为面板内字段 */
  groupPanels?: Partial<Record<FieldGroup, { outer?: string[]; inner: string[] }>>
  /** 标签高亮显示的字段 prop */
  highlightLabels?: string[]
}>()

function formItemClass(prop: string) {
  return props.highlightLabels?.includes(prop) ? 'form-item--highlight-label' : undefined
}

function groupPanel(group: FieldGroup) {
  return props.groupPanels?.[group]
}

function panelOuterFields(group: { group: FieldGroup; fields: FieldSchema[] }) {
  const panel = groupPanel(group.group)
  if (!panel) return []
  const innerSet = new Set(panel.inner)
  const outerSet = panel.outer ? new Set(panel.outer) : null
  return group.fields.filter((f) => {
    if (innerSet.has(f.prop)) return false
    if (outerSet) return outerSet.has(f.prop)
    return true
  })
}

function panelInnerFields(group: { group: FieldGroup; fields: FieldSchema[] }) {
  const panel = groupPanel(group.group)
  if (!panel) return []
  const map = new Map(group.fields.map((f) => [f.prop, f]))
  return panel.inner.map((prop) => map.get(prop)).filter((f): f is FieldSchema => !!f)
}

function panelOuterGridStyle(group: FieldGroup) {
  const cols = groupColumns(group) ?? 2
  return { gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }
}

function panelOuterFieldRows(group: { group: FieldGroup; fields: FieldSchema[] }) {
  const rows = props.groupRows?.[group.group]
  if (!rows?.length) return [] as FieldSchema[][]
  const map = new Map(panelOuterFields(group).map((f) => [f.prop, f]))
  return rows
    .map((row) => row.map((prop) => map.get(prop)).filter((f): f is FieldSchema => !!f))
    .filter((row) => row.length > 0)
}

function groupFieldRows(group: { group: FieldGroup; fields: FieldSchema[] }) {
  const rows = props.groupRows?.[group.group]
  if (!rows?.length) return [] as FieldSchema[][]
  const map = new Map(group.fields.map((f) => [f.prop, f]))
  return rows
    .map((row) => row.map((prop) => map.get(prop)).filter((f): f is FieldSchema => !!f))
    .filter((row) => row.length > 0)
}

function sectionTitle(group: FieldGroup) {
  return props.groupTitles?.[group] ?? groupTitle(group)
}

function groupColumns(group: FieldGroup) {
  return props.groupColumns?.[group]
}

function formGridClass(group: FieldGroup) {
  const cols = groupColumns(group)
  return cols && cols >= 4 ? 'form-grid--dense' : undefined
}

function gridItemStyle(field: FieldSchema, group: FieldGroup) {
  const cols = groupColumns(group)
  if (!cols) return undefined
  if (field.span && field.span >= 24) return { gridColumn: '1 / -1' }
  if (field.type === 'textarea') return { gridColumn: '1 / -1' }
  return undefined
}

function fieldSpan(field: FieldSchema, group: FieldGroup) {
  if (field.span) return field.span
  if (props.groupSpan?.[group]) return props.groupSpan[group]!
  return 12
}

const groups = computed(() => {
  if (props.fields?.length) return buildGroups(props.fields)
  return getGroupedFields(props.table)
})
const flatFields = computed(() => props.fields ?? getSchema(props.table).filter((f) => !f.readonly))
</script>

<style scoped>
.form-grid-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-grid {
  display: grid;
  gap: 4px 16px;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 12px;
  min-width: 0;
}

.form-grid :deep(.el-form-item__label) {
  width: 128px !important;
  padding-right: 6px;
  white-space: nowrap;
  flex-shrink: 0;
}

.form-grid--dense :deep(.el-form-item) {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 10px;
}

.form-grid--dense :deep(.el-form-item__label) {
  width: auto !important;
  max-width: none;
  min-width: 72px;
  padding: 0 6px 0 0;
  line-height: 1.25;
  white-space: nowrap;
  text-align: right;
  font-size: 12px;
  height: auto !important;
  flex-shrink: 0;
}

.form-grid--dense :deep(.el-form-item__content) {
  margin-left: 0 !important;
  flex: 1;
  min-width: 0;
}

.form-grid :deep(.el-form-item__content) {
  flex: 1;
  min-width: 0;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-input),
.form-grid :deep(.el-input-number) {
  width: 100%;
}

.form-group-panel {
  margin-top: 4px;
  padding: 12px 16px 4px;
  background: var(--meis-surface-muted);
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
}

.form-group-panel__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-start;
  gap: 4px 20px;
}

.form-group-panel__row :deep(.el-form-item) {
  margin-bottom: 8px;
  flex: 0 0 auto;
  width: auto;
}

.form-group-panel__row :deep(.el-form-item__label) {
  width: auto !important;
  padding-right: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}

.form-group-panel__row :deep(.el-form-item__content) {
  flex: 0 0 auto;
}

.form-group-panel__item {
  margin-bottom: 0;
}

:deep(.form-item--highlight-label .el-form-item__label) {
  color: #d03030 !important;
}
</style>
