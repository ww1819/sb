<template>
  <div class="grouped-form">
    <template v-if="groups.length">
      <FormSection v-for="g in groups" :key="g.group" :title="groupTitle(g.group)">
        <el-row :gutter="16">
          <el-col v-for="f in g.fields" :key="f.prop" :span="f.span ?? 12">
            <el-form-item :label="f.label" :required="f.required">
              <FieldRenderer v-model="model[f.prop]" :field="f" />
            </el-form-item>
          </el-col>
        </el-row>
      </FormSection>
    </template>
    <template v-else>
      <el-form-item v-for="f in flatFields" :key="f.prop" :label="f.label" :required="f.required">
        <FieldRenderer v-model="model[f.prop]" :field="f" />
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import FormSection from '@/components/form/FormSection.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import { getGroupedFields, getSchema, groupTitle, type FieldSchema } from '@/config/pageSchemas'

const props = defineProps<{
  table: string
  model: Record<string, unknown>
  fields?: FieldSchema[]
}>()

const groups = computed(() => getGroupedFields(props.table))
const flatFields = computed(() => props.fields ?? getSchema(props.table).filter((f) => !f.readonly))
</script>
