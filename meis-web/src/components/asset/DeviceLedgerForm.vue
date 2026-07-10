<template>
  <el-form label-width="120px" class="device-ledger-form">
    <FormTabNav v-model="activeTab" :tabs="tabs" />

    <div class="device-ledger-form__panel">
      <GroupedFormFields
        v-show="activeTab === 'basic'"
        table="medical_device"
        :model="model"
        :fields="basicFields"
      />

      <div v-show="activeTab === 'card'" class="device-ledger-form__card-pane">
        <DeviceAssetCard :model="model" />
      </div>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import FormTabNav from '@/components/form/FormTabNav.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import DeviceAssetCard from '@/components/asset/DeviceAssetCard.vue'
import type { FieldSchema } from '@/config/pageSchemas'

const props = defineProps<{
  model: Record<string, unknown>
  fields: FieldSchema[]
}>()

const activeTab = ref('basic')

const tabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'card', label: '资产卡片' }
]

const basicGroupKeys = new Set(['basic', 'finance', 'location', 'time', 'status', 'attachment', 'remark', 'other'])

const basicFields = computed(() =>
  props.fields.filter((f) => basicGroupKeys.has(f.group ?? 'other'))
)
</script>

<style scoped>
.device-ledger-form {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.device-ledger-form__panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.device-ledger-form__card-pane {
  padding-top: 4px;
}
</style>
