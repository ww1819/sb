<template>
  <el-card shadow="never" class="panel-card traffic-way-panel">
    <template #header>
      <div class="panel-header">交通方式</div>
    </template>
    <div ref="chartRef" class="traffic-way-chart" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { buildTrafficWayOption } from '@/composables/buildTrafficWayOption'

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  chart.setOption(buildTrafficWayOption())
  chart.getZr().on('mousemove', () => {
    if (chartRef.value) chartRef.value.style.cursor = 'pointer'
  })
  chart.getZr().on('globalout', () => {
    if (chartRef.value) chartRef.value.style.cursor = 'default'
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.traffic-way-panel {
  margin-top: 16px;
}

.traffic-way-panel :deep(.el-card__header) {
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

.traffic-way-panel :deep(.el-card__body) {
  padding: 8px 0 12px;
}

.traffic-way-chart {
  width: 100%;
  height: 420px;
}
</style>
