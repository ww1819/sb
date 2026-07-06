<template>
  <div class="module-page">
    <Suspense v-if="pageComponent">
      <component :is="pageComponent" />
      <template #fallback>
        <div v-loading="true" class="module-page-loading" />
      </template>
    </Suspense>
    <el-empty v-else-if="config" description="加载中..." />
    <el-empty v-else description="未配置页面" />
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, h } from 'vue'
import { useRoute } from 'vue-router'
import { getPageConfig } from '@/config/pageRegistry'
import CrudPage from '@/components/CrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const route = useRoute()
const path = computed(() => '/' + (route.params.module as string) + '/' + (route.params.page as string))
const config = computed(() => getPageConfig(path.value))

const pageMap: Record<string, ReturnType<typeof defineAsyncComponent>> = {
  '/purchase/plan': defineAsyncComponent(() => import('@/views/purchase/PurchasePlanPage.vue')),
  '/purchase/project': defineAsyncComponent(() => import('@/views/purchase/PurchaseProjectPage.vue')),
  '/purchase/contract': defineAsyncComponent(() => import('@/views/purchase/PurchaseContractPage.vue')),
  '/purchase/supplier': defineAsyncComponent(() => import('@/views/purchase/SupplierPage.vue')),
  '/purchase/category': defineAsyncComponent(() => import('@/views/purchase/DeviceCategoryPage.vue')),
  '/asset/device': defineAsyncComponent(() => import('@/views/asset/DeviceListPage.vue')),
  '/asset/entry': defineAsyncComponent(() => import('@/views/asset/DeviceEntryPage.vue')),
  '/asset/outbound': defineAsyncComponent(() => import('@/views/asset/DeviceOutboundPage.vue')),
  '/asset/transfer': defineAsyncComponent(() => import('@/views/asset/AssetTransferPage.vue')),
  '/asset/inventory': defineAsyncComponent(() => import('@/views/asset/InventoryCheckPage.vue')),
  '/asset/scrap': defineAsyncComponent(() => import('@/views/asset/DeviceScrapPage.vue')),
  '/asset/inspection': defineAsyncComponent(() => import('@/views/asset/InspectionPage.vue')),
  '/repair/workorder': defineAsyncComponent(() => import('@/views/repair/WorkorderListPage.vue')),
  '/repair/engineer': defineAsyncComponent(() => import('@/views/repair/EngineerPage.vue')),
  '/repair/spare': defineAsyncComponent(() => import('@/views/repair/SparePartPage.vue')),
  '/repair/fault': defineAsyncComponent(() => import('@/views/repair/FaultTypePage.vue')),
  '/maintain/template': defineAsyncComponent(() => import('@/views/maintain/MaintainTemplatePage.vue')),
  '/maintain/plan': defineAsyncComponent(() => import('@/views/maintain/MaintainPlanPage.vue')),
  '/maintain/record': defineAsyncComponent(() => import('@/views/maintain/MaintainRecordPage.vue')),
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
  const cfg = config.value
  if (cfg) {
    return {
      setup() {
        const pageConfig: PageConfig = cfg
        return () => h(CrudPage, { config: pageConfig })
      }
    }
  }
  return null
})
</script>

<style scoped>
.module-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.module-page-loading {
  height: 100%;
  min-height: 200px;
}
</style>
