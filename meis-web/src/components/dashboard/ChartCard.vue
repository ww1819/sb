<template>
  <el-card shadow="never" class="chart-card">
    <template #header>
      <div class="chart-card-header">
        <span class="chart-card-title">{{ title }}</span>
        <slot name="extra" />
      </div>
    </template>
    <div ref="chartRef" class="chart-card-body" :style="{ height }" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { useLayoutStore } from '@/stores/layout'

const props = withDefaults(
  defineProps<{
    title: string
    option: EChartsOption
    height?: string
  }>(),
  { height: '260px' }
)

const chartRef = ref<HTMLElement>()
const layoutStore = useLayoutStore()
let chart: echarts.ECharts | null = null
let optionSig = ''

function optionSignature(option: EChartsOption) {
  try {
    return JSON.stringify(option)
  } catch {
    return String(Date.now())
  }
}

function render() {
  if (!chartRef.value || !props.option) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.getZr().on('mousemove', () => {
      const el = chartRef.value
      if (el) el.style.cursor = 'pointer'
    })
    chart.getZr().on('globalout', () => {
      const el = chartRef.value
      if (el) el.style.cursor = 'default'
    })
  }
  const nextSig = optionSignature(props.option)
  if (nextSig === optionSig) return
  optionSig = nextSig
  chart.setOption(props.option, true)
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

watch(() => props.option, render, { deep: true })
watch(() => layoutStore.themeRevision, () => {
  optionSig = ''
  render()
})
</script>

<style scoped>
.chart-card {
  border-radius: var(--meis-card-radius);
  border: 1px solid var(--meis-border-light);
  box-shadow: var(--meis-card-shadow);
}

.chart-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid var(--meis-border-light);
  background: #fff;
}

.chart-card :deep(.el-card__body) {
  padding: 12px 16px 16px;
}

.chart-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chart-card-title {
  position: relative;
  padding-left: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.chart-card-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  background: var(--el-color-primary);
}

.chart-card-body {
  width: 100%;
}
</style>
