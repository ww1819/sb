<template>
  <div class="device-list-page">
    <CrudPage
      :config="config"
      enable-view
      delete-url="/asset/device"
      :can-delete="(row) => row.can_delete !== false"
    >
      <template #form="{ form, fields, mode }">
        <DeviceLedgerForm :model="form" :fields="fields" :mode="mode" />
      </template>
    </CrudPage>
  </div>
</template>

<script setup lang="ts">
import CrudPage from '@/components/CrudPage.vue'
import DeviceLedgerForm from '@/components/asset/DeviceLedgerForm.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/asset/device')!
</script>

<style scoped>
.device-list-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
