<template>
  <div class="stock-query-page">
    <CrudPage
      ref="crudRef"
      :config="config"
      enable-view
      hide-add
      delete-url="/asset/device"
      :can-delete="() => false"
    >
      <template #form="{ form, fields, mode }">
        <DeviceLedgerForm :model="form" :fields="fields" :mode="mode" />
      </template>
    </CrudPage>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/CrudPage.vue'
import DeviceLedgerForm from '@/components/asset/DeviceLedgerForm.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/asset/stock')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
</script>

<style scoped>
.stock-query-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
