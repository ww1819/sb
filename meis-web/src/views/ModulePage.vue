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
  '/purchase/apply': defineAsyncComponent(() => import('@/views/purchase/PurchaseApplyPage.vue')),
  '/purchase/approval': defineAsyncComponent(() => import('@/views/purchase/PurchaseApprovalPage.vue')),
  '/purchase/project': defineAsyncComponent(() => import('@/views/purchase/PurchaseProjectPage.vue')),
  '/purchase/contract': defineAsyncComponent(() => import('@/views/purchase/PurchaseContractPage.vue')),
  '/purchase/acceptance': defineAsyncComponent(() => import('@/views/purchase/PurchaseAcceptancePage.vue')),
  '/purchase/supplier': defineAsyncComponent(() => import('@/views/purchase/SupplierPage.vue')),
  '/purchase/category': defineAsyncComponent(() => import('@/views/purchase/DeviceCategoryPage.vue')),
  '/purchase/manufacturer': defineAsyncComponent(() => import('@/views/purchase/ManufacturerPage.vue')),
  '/purchase/dashboard': defineAsyncComponent(() => import('@/views/purchase/PurchaseDashboardPage.vue')),
  '/purchase/trace': defineAsyncComponent(() => import('@/views/purchase/PurchaseTracePage.vue')),
  '/purchase/report': defineAsyncComponent(() => import('@/views/purchase/PurchaseReportPage.vue')),
  '/asset/query': defineAsyncComponent(() => import('@/views/asset/AssetQueryPage.vue')),
  '/asset/import': defineAsyncComponent(() => import('@/views/asset/AssetImportPage.vue')),
  '/asset/device': defineAsyncComponent(() => import('@/views/asset/DeviceListPage.vue')),
  '/asset/entry': defineAsyncComponent(() => import('@/views/asset/DeviceEntryPage.vue')),
  '/asset/stock': defineAsyncComponent(() => import('@/views/asset/StockQueryPage.vue')),
  '/asset/outbound': defineAsyncComponent(() => import('@/views/asset/DeviceOutboundPage.vue')),
  '/asset/transfer': defineAsyncComponent(() => import('@/views/asset/AssetTransferPage.vue')),
  '/asset/inventory': defineAsyncComponent(() => import('@/views/asset/InventoryCheckPage.vue')),
  '/asset/scrap': defineAsyncComponent(() => import('@/views/asset/DeviceScrapPage.vue')),
  '/asset/inspection': defineAsyncComponent(() => import('@/views/asset/InspectionPage.vue')),
  '/repair/apply': defineAsyncComponent(() => import('@/views/repair/WorkorderListPage.vue')),
  '/repair/handle': defineAsyncComponent(() => import('@/views/repair/WorkorderListPage.vue')),
  '/repair/verify': defineAsyncComponent(() => import('@/views/repair/WorkorderListPage.vue')),
  '/repair/workorder': defineAsyncComponent(() => import('@/views/repair/WorkorderListPage.vue')),
  '/repair/engineer': defineAsyncComponent(() => import('@/views/repair/EngineerPage.vue')),
  '/repair/spare-archive': defineAsyncComponent(() => import('@/views/repair/SparePartPage.vue')),
  '/repair/fault': defineAsyncComponent(() => import('@/views/repair/FaultTypePage.vue')),
  '/repair/process-type': defineAsyncComponent(() => import('@/views/repair/ProcessTypePage.vue')),
  '/maintain/param': defineAsyncComponent(() => import('@/views/maintain/MaintainParamPage.vue')),
  '/maintain/plan': defineAsyncComponent(() => import('@/views/maintain/MaintainPlanPage.vue')),
  '/maintain/execution': defineAsyncComponent(() => import('@/views/maintain/MaintainExecutionPage.vue')),
  '/maintain/query': defineAsyncComponent(() => import('@/views/maintain/MaintainQueryPage.vue')),
  '/inspect/param': defineAsyncComponent(() => import('@/views/inspect/InspectParamPage.vue')),
  '/inspect/plan': defineAsyncComponent(() => import('@/views/inspect/InspectPlanPage.vue')),
  '/inspect/execution': defineAsyncComponent(() => import('@/views/inspect/InspectExecutionPage.vue')),
  '/inspect/query': defineAsyncComponent(() => import('@/views/inspect/InspectQueryPage.vue')),
  '/metrology/param': defineAsyncComponent(() => import('@/views/metrology/MetrologyParamPage.vue')),
  '/metrology/plan': defineAsyncComponent(() => import('@/views/metrology/MetrologyPlanPage.vue')),
  '/metrology/execution': defineAsyncComponent(() => import('@/views/metrology/MetrologyExecutionPage.vue')),
  '/metrology/query': defineAsyncComponent(() => import('@/views/metrology/MetrologyQueryPage.vue')),
  '/maintain/template': defineAsyncComponent(() => import('@/views/maintain/MaintainTemplatePage.vue')),
  '/maintain/record': defineAsyncComponent(() => import('@/views/maintain/MaintainRecordPage.vue')),
  '/system/user': defineAsyncComponent(() => import('@/views/system/UserPage.vue')),
  '/system/role': defineAsyncComponent(() => import('@/views/system/RolePage.vue')),
  '/system/campus': defineAsyncComponent(() => import('@/views/system/CampusPage.vue')),
  '/dict/campus': defineAsyncComponent(() => import('@/views/system/CampusPage.vue')),
  '/dict/supplier': defineAsyncComponent(() => import('@/views/purchase/SupplierPage.vue')),
  '/dict/manufacturer': defineAsyncComponent(() => import('@/views/purchase/ManufacturerPage.vue')),
  '/dict/category': defineAsyncComponent(() => import('@/views/purchase/DeviceCategoryPage.vue')),
  '/dict/dept': defineAsyncComponent(() => import('@/views/system/DeptPage.vue')),
  '/dict/warehouse': defineAsyncComponent(() => import('@/views/system/WarehousePage.vue')),
  '/system/warehouse': defineAsyncComponent(() => import('@/views/system/WarehousePage.vue')),
  '/system/dept': defineAsyncComponent(() => import('@/views/system/DeptPage.vue')),
  '/system/dict': defineAsyncComponent(() => import('@/views/system/DictPage.vue')),
  '/system/log': defineAsyncComponent(() => import('@/views/system/OperationLogPage.vue')),
  '/system/approval': defineAsyncComponent(() => import('@/views/system/ApprovalConfigPage.vue')),
  '/system/supplier': defineAsyncComponent(() => import('@/views/purchase/SupplierPage.vue')),
  '/system/category': defineAsyncComponent(() => import('@/views/purchase/DeviceCategoryPage.vue')),
  '/system/manufacturer': defineAsyncComponent(() => import('@/views/purchase/ManufacturerPage.vue')),
  '/system/config': defineAsyncComponent(() => import('@/views/system/SystemConfigPage.vue')),
  '/warehouse/setting': defineAsyncComponent(() => import('@/views/system/WarehousePage.vue')),
  '/warehouse/entry': defineAsyncComponent(() => import('@/views/asset/DeviceEntryPage.vue')),
  '/warehouse/outbound': defineAsyncComponent(() => import('@/views/warehouse/WarehouseOutboundPage.vue')),
  '/warehouse/return': defineAsyncComponent(() => import('@/views/warehouse/WarehouseReturnPage.vue')),
  '/warehouse/transfer': defineAsyncComponent(() => import('@/views/warehouse/WarehouseTransferPage.vue')),
  '/warehouse/inventory': defineAsyncComponent(() => import('@/views/asset/InventoryCheckPage.vue')),
  '/warehouse/scrap': defineAsyncComponent(() => import('@/views/warehouse/WarehouseScrapPage.vue')),
  '/qc/adverse/report': defineAsyncComponent(() => import('@/views/qc/AdverseReportPage.vue')),
  '/qc/adverse/query': defineAsyncComponent(() => import('@/views/qc/AdverseQueryPage.vue')),
  '/special/life': defineAsyncComponent(() => import('@/views/special/LifeSupportPage.vue')),
  '/special/radiation': defineAsyncComponent(() => import('@/views/special/SpecialRadiationPage.vue')),
  '/special/emergency': defineAsyncComponent(() => import('@/views/special/EmergencyPoolPage.vue')),
  '/special/leased': defineAsyncComponent(() => import('@/views/special/LeasedDevicePage.vue')),
  '/special/alerts': defineAsyncComponent(() => import('@/views/special/SpecialAlertPage.vue')),
  '/shared/device': defineAsyncComponent(() => import('@/views/shared/SharedDevicePage.vue')),
  '/shared/loan': defineAsyncComponent(() => import('@/views/shared/SharedLoanPage.vue')),
  '/shared/loan-approve': defineAsyncComponent(() => import('@/views/shared/SharedLoanApprovePage.vue')),
  '/shared/return': defineAsyncComponent(() => import('@/views/shared/SharedReturnPage.vue')),
  '/shared/return-approve': defineAsyncComponent(() => import('@/views/shared/SharedReturnApprovePage.vue')),
  '/shared/fee': defineAsyncComponent(() => import('@/views/shared/SharedFeePage.vue')),
  '/shared/record': defineAsyncComponent(() => import('@/views/shared/SharedRecordPage.vue')),
  '/pm/param': defineAsyncComponent(() => import('@/views/pm/PmParamPage.vue')),
  '/pm/plan': defineAsyncComponent(() => import('@/views/pm/PmPlanPage.vue')),
  '/pm/execution': defineAsyncComponent(() => import('@/views/pm/PmExecutionPage.vue')),
  '/pm/query': defineAsyncComponent(() => import('@/views/pm/PmQueryPage.vue')),
  '/analytics/mapping': defineAsyncComponent(() => import('@/views/analytics/BenefitMappingPage.vue')),
  '/analytics/sync': defineAsyncComponent(() => import('@/views/analytics/BenefitSyncPage.vue')),
  '/analytics/summary': defineAsyncComponent(() => import('@/views/analytics/BenefitSummaryPage.vue')),
  '/analytics/cost': defineAsyncComponent(() => import('@/views/analytics/BenefitCostPage.vue')),
  '/analytics/device': defineAsyncComponent(() => import('@/views/analytics/BenefitDevicePage.vue')),
  '/power/station': defineAsyncComponent(() => import('@/views/power/PowerStationPage.vue')),
  '/power/tag': defineAsyncComponent(() => import('@/views/power/PowerTagPage.vue')),
  '/power/status': defineAsyncComponent(() => import('@/views/power/PowerStatusPage.vue')),
  '/power/stats': defineAsyncComponent(() => import('@/views/power/PowerStatsPage.vue')),
  '/power/record': defineAsyncComponent(() => import('@/views/power/PowerRecordPage.vue')),
  '/screen/equipment': defineAsyncComponent(() => import('@/views/screen/EquipmentScreenPage.vue')),
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

