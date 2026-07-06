import type { EChartsOption } from 'echarts'

export const CHART_COLORS = ['#1677ff', '#13c2c2', '#722ed1', '#fa8c16', '#52c41a', '#eb2f96']

function isDarkTheme() {
  return document.documentElement.classList.contains('dark')
}

const axisStyle = () => ({
  axisLine: { lineStyle: { color: isDarkTheme() ? '#434343' : '#ebeef5' } },
  axisTick: { show: false },
  axisLabel: { color: isDarkTheme() ? '#a3a6ad' : '#909399', fontSize: 12 }
})

function baseTooltip(): EChartsOption['tooltip'] {
  const dark = isDarkTheme()
  return {
    trigger: 'axis',
    backgroundColor: dark ? 'rgba(29, 29, 29, 0.96)' : 'rgba(255, 255, 255, 0.96)',
    borderColor: dark ? '#434343' : '#ebeef5',
    borderWidth: 1,
    textStyle: { color: dark ? '#e5eaf3' : '#303133', fontSize: 12 },
    extraCssText: 'box-shadow: 0 4px 12px rgba(0, 21, 41, 0.08); border-radius: 6px;'
  }
}

function baseGrid(): EChartsOption['grid'] {
  return { left: 48, right: 24, top: 32, bottom: 32, containLabel: true }
}

export function buildLineOption(
  categories: string[],
  values: number[],
  name = '数量'
): EChartsOption {
  return {
    color: CHART_COLORS,
    tooltip: baseTooltip(),
    grid: baseGrid(),
    xAxis: {
      type: 'category',
      data: categories,
      boundaryGap: false,
      ...axisStyle()
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: isDarkTheme() ? '#303030' : '#f0f2f5', type: 'dashed' } },
      ...axisStyle()
    },
    series: [
      {
        name,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 3 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(22, 119, 255, 0.25)' },
              { offset: 1, color: 'rgba(22, 119, 255, 0.02)' }
            ]
          }
        },
        data: values
      }
    ]
  }
}

export function buildBarOption(
  categories: string[],
  values: number[],
  name = '数量'
): EChartsOption {
  return {
    color: CHART_COLORS,
    tooltip: baseTooltip(),
    grid: baseGrid(),
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: isDarkTheme() ? '#a3a6ad' : '#909399', fontSize: 12, rotate: categories.length > 6 ? 30 : 0 },
      axisLine: { lineStyle: { color: isDarkTheme() ? '#434343' : '#ebeef5' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: isDarkTheme() ? '#303030' : '#f0f2f5', type: 'dashed' } },
      ...axisStyle()
    },
    series: [
      {
        name,
        type: 'bar',
        barMaxWidth: 28,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        data: values
      }
    ]
  }
}

export function buildPieOption(
  data: { name: string; value: number }[],
  name = '占比'
): EChartsOption {
  return {
    color: CHART_COLORS,
    tooltip: {
      trigger: 'item',
      backgroundColor: isDarkTheme() ? 'rgba(29, 29, 29, 0.96)' : 'rgba(255, 255, 255, 0.96)',
      borderColor: isDarkTheme() ? '#434343' : '#ebeef5',
      borderWidth: 1,
      textStyle: { color: isDarkTheme() ? '#e5eaf3' : '#303133', fontSize: 12 },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: isDarkTheme() ? '#a3a6ad' : '#606266', fontSize: 12 }
    },
    series: [
      {
        name,
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: isDarkTheme() ? '#1d1d1d' : '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 13, fontWeight: 600 }
        },
        data
      }
    ]
  }
}
