<template>
  <div class="grouped-form">
    <template v-if="groups.length">
      <FormSection v-for="g in groups" :key="g.group" :title="sectionTitle(g.group)">
        <div
          v-if="groupColumns(g.group)"
          class="form-grid"
          :style="{ gridTemplateColumns: `repeat(${groupColumns(g.group)}, minmax(0, 1fr))` }"
        >
          <el-form-item
            v-for="f in g.fields"
            :key="f.prop"
            :label="f.label"
            :required="f.required"
          >
            <slot :name="`field-${f.prop}`" :field="f" :model="model">
              <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
            </slot>
          </el-form-item>
        </div>
        <el-row v-else :gutter="16">
          <el-col v-for="f in g.fields" :key="f.prop" :span="fieldSpan(f, g.group)">
            <el-form-item :label="f.label" :required="f.required">
              <slot :name="`field-${f.prop}`" :field="f" :model="model">
                <FieldRenderer v-model="model[f.prop]" :field="f" :model="model" />
              </slot>
            </el-form-item>
          </el-col>
        </el-row>
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
  const order = ['basic', 'finance', 'location', 'vendor', 'time', 'status', 'workflow', 'approval', 'compliance', 'attachment', 'remark', 'other']
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
}>()

function sectionTitle(group: FieldGroup) {
  return props.groupTitles?.[group] ?? groupTitle(group)
}

function groupColumns(group: FieldGroup) {
  return props.groupColumns?.[group]
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

.form-grid :deep(.el-form-item__content) {
  flex: 1;
  min-width: 0;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-input),
.form-grid :deep(.el-input-number) {
  width: 100%;
}
</style>
