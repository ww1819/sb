<template>
  <el-card shadow="never" class="panel-card asset-dist-panel">
    <template #header>
      <div class="panel-header">资产分布</div>
    </template>
    <div v-if="!hasData" class="asset-dist-empty">
      <PageEmpty description="暂无资产分布数据" :image-size="72" />
    </div>
    <div v-show="hasData" ref="chartRef" class="asset-dist-chart" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import PageEmpty from '@/components/table/PageEmpty.vue'
import {
  buildAssetDistributionOption,
  mergeTopDeptCounts,
  type DeptCountItem
} from '@/composables/buildAssetDistributionOption'

const props = defineProps<{
  items: DeptCountItem[]
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const hasData = computed(() => mergeTopDeptCounts(props.items).length > 0)

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  if (!hasData.value) {
    chart.clear()
    return
  }
  chart.setOption(buildAssetDistributionOption(props.items), true)
}

function handleResize() {
  chart?.resize()
}

watch(
  () => props.items,
  () => {
    render()
  },
  { deep: true }
)

watch(hasData, (ok) => {
  if (ok) {
    requestAnimationFrame(() => {
      render()
      chart?.resize()
    })
  }
})

onMounted(() => {
  render()
  if (chart) {
    chart.getZr().on('mousemove', () => {
      if (chartRef.value && hasData.value) chartRef.value.style.cursor = 'pointer'
    })
    chart.getZr().on('globalout', () => {
      if (chartRef.value) chartRef.value.style.cursor = 'default'
    })
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.asset-dist-panel {
  margin-top: 0;
}

.asset-dist-panel :deep(.el-card__header) {
  padding: 10px 16px;
  border-bottom: 1px solid var(--meis-border-light);
}

.panel-header {
  position: relative;
  padding-left: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.panel-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  background: var(--el-color-primary);
}

.asset-dist-panel :deep(.el-card__body) {
  padding: 8px 0 12px;
}

.asset-dist-chart {
  width: 100%;
  height: 300px;
}

.asset-dist-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}
</style>
