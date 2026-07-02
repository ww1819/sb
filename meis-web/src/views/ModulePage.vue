<template>
  <component :is="pageComponent" v-if="pageComponent" />
  <el-empty v-else-if="config" description="加载中..." />
  <el-empty v-else description="未配置页面" />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { getPageConfig } from '@/config/pageRegistry'
import CrudPage from '@/components/CrudPage.vue'

const route = useRoute()
const path = computed(() => '/' + (route.params.module as string) + '/' + (route.params.page as string))
const config = computed(() => getPageConfig(path.value))

const pageMap: Record<string, ReturnType<typeof defineAsyncComponent>> = {
  '/purchase/plan': defineAsyncComponent(() => import('@/views/purchase/PurchasePlanPage.vue')),
  '/purchase/project': defineAsyncComponent(() => import('@/views/purchase/PurchaseProjectPage.vue')),
  '/purchase/contract': defineAsyncComponent(() => import('@/views/purchase/PurchaseContractPage.vue')),
  '/asset/device': defineAsyncComponent(() => import('@/views/asset/DeviceListPage.vue')),
  '/asset/entry': defineAsyncComponent(() => import('@/views/asset/DeviceEntryPage.vue')),
  '/asset/outbound': defineAsyncComponent(() => import('@/views/asset/DeviceOutboundPage.vue')),
  '/asset/transfer': defineAsyncComponent(() => import('@/views/asset/AssetTransferPage.vue')),
  '/repair/workorder': defineAsyncComponent(() => import('@/views/repair/WorkorderListPage.vue')),
  '/system/user': defineAsyncComponent(() => import('@/views/system/UserPage.vue')),
  '/system/role': defineAsyncComponent(() => import('@/views/system/RolePage.vue')),
  '/system/campus': defineAsyncComponent(() => import('@/views/system/CampusPage.vue')),
  '/system/dept': defineAsyncComponent(() => import('@/views/system/DeptPage.vue')),
  '/system/dict': defineAsyncComponent(() => import('@/views/system/DictPage.vue')),
  '/system/log': defineAsyncComponent(() => import('@/views/system/OperationLogPage.vue')),
  '/system/approval': defineAsyncComponent(() => import('@/views/system/ApprovalConfigPage.vue')),
  '/system/warehouse': defineAsyncComponent(() => import('@/views/system/WarehousePage.vue')),
}

const pageComponent = computed(() => {
  const specialized = pageMap[path.value]
  if (specialized) return specialized
  if (config.value) {
    return {
      setup() {
        return () => h(CrudPage, { config: config.value })
      }
    }
  }
  return null
})

import { h } from 'vue'
</script>
