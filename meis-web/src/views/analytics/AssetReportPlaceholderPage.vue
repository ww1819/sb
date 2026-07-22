<template>
  <FeaturePlaceholderPage
    :title="title"
    subtitle="资产报表统计"
    :description="`${title}功能开发中`"
    hint="菜单已开通（ANA-UI-03）；完整能力见待开发池 BACKLOG-ANA-04~12"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import FeaturePlaceholderPage from '@/components/FeaturePlaceholderPage.vue'

const TITLES: Record<string, string> = {
  '/analytics/asset-usage': '资产使用率统计',
  '/analytics/value-structure': '价值结构分析表',
  '/analytics/depr-due': '折旧到期汇总',
  '/analytics/depr-stats': '资产折旧统计',
  '/analytics/depr-ratio': '折旧明细比例',
  '/analytics/depr-detail': '折旧详情',
  '/analytics/asset-change': '资产增减统计',
  '/analytics/asset-occupy': '资产占用统计',
  '/analytics/asset-transfer': '资产异动统计'
}

const route = useRoute()
const title = computed(() => TITLES[route.path] || '资产报表统计')
</script>
