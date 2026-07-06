<template>
  <StatusTag v-if="showStatus" :value="value" :prop="field.prop" />
  <span v-else-if="showAmount" class="cell-number cell-amount">{{ formattedNumber }}</span>
  <span v-else-if="showNumeric" class="cell-number">{{ formattedNumber }}</span>
  <span v-else>{{ displayText }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StatusTag from './StatusTag.vue'
import type { FieldSchema } from '@/config/pageSchemas'
import {
  formatCellNumber,
  formatStatusLabel,
  isAmountField,
  isBooleanField,
  isNumericField,
  isStatusField
} from '@/utils/tableCell'

const props = defineProps<{
  field: FieldSchema
  value: unknown
}>()

const showStatus = computed(
  () => isStatusField(props.field.prop, props.field.dictType) || isBooleanField(props.field.prop, props.value)
)
const showAmount = computed(() => isAmountField(props.field.prop, props.field.type))
const showNumeric = computed(() => isNumericField(props.field.prop, props.field.type))

const formattedNumber = computed(() => formatCellNumber(props.value, showAmount.value))
const displayText = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '-'
  return formatStatusLabel(props.value, props.field.prop)
})
</script>

<style scoped>
.cell-number {
  display: inline-block;
  width: 100%;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--meis-text-primary);
}

.cell-amount {
  font-weight: 600;
  color: var(--meis-status-info);
}
</style>
