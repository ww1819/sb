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

/** 南丁格尔玫瑰图（空心 + 圆角扇区 + 外侧标签/引导线），对齐 ECharts 官方示例 */
export const ROSE_CHART_COLORS = [
  '#5470c6',
  '#91cc75',
  '#3f4b8a',
  '#fc8452',
  '#73c0de',
  '#fac858',
  '#ee6666',
  '#9a60b4',
  '#ea7ccc'
]

export function buildRosePieOption(
  data: { name: string; value: number }[],
  name = '占比'
): EChartsOption {
  const dark = isDarkTheme()
  return {
    color: ROSE_CHART_COLORS,
    tooltip: {
      trigger: 'item',
      backgroundColor: dark ? 'rgba(29, 29, 29, 0.96)' : 'rgba(255, 255, 255, 0.96)',
      borderColor: dark ? '#434343' : '#ebeef5',
      borderWidth: 1,
      textStyle: { color: dark ? '#e5eaf3' : '#303133', fontSize: 12 },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      orient: 'horizontal',
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: dark ? '#a3a6ad' : '#606266', fontSize: 12 }
    },
    series: [
      {
        name,
        type: 'pie',
        roseType: 'area',
        radius: ['18%', '58%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 8 },
        label: {
          show: true,
          color: dark ? '#cfd3dc' : '#606266',
          fontSize: 11
        },
        labelLine: {
          show: true,
          length: 12,
          length2: 8,
          lineStyle: { width: 1 }
        },
        data
      }
    ]
  }
}

export interface MultiKpiRing {
  name: string
  value: number
  color: string
  trackColor: string
}

/** Highcharts 风格多环 KPI 仪表（同心 pie + 悬停放大/Tooltip） */
export function buildMultiKpiGaugeOption(
  rings: MultiKpiRing[],
  centerTitle = 'Conversion',
  centerValue = '80%'
): EChartsOption {
  const dark = isDarkTheme()

  const series = rings.map((ring, index) => {
    const outer = 78 - index * 14
    const inner = outer - 10
    const tipHtml = `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${ring.color};margin-right:6px;"></span>${ring.name}: <b>${ring.value}%</b>`
    return {
      type: 'pie' as const,
      name: ring.name,
      radius: [`${inner}%`, `${outer}%`],
      center: ['50%', '52%'],
      startAngle: 90,
      clockwise: true,
      avoidLabelOverlap: false,
      label: { show: false },
      labelLine: { show: false },
      // 整环（进度+轨道）均可悬停
      emphasis: {
        scale: true,
        scaleSize: 6,
        itemStyle: {
          shadowBlur: 18,
          shadowColor: 'rgba(0, 0, 0, 0.3)'
        }
      },
      data: [
        {
          value: ring.value,
          name: ring.name,
          itemStyle: {
            color: ring.color,
            borderRadius: 20
          },
          tooltip: { formatter: () => tipHtml }
        },
        {
          value: Math.max(0.01, 100 - ring.value),
          name: ring.name,
          itemStyle: {
            color: ring.trackColor,
            borderRadius: 20
          },
          tooltip: { formatter: () => tipHtml }
        }
      ],
      animationType: 'scale' as const,
      animationDuration: 700,
      z: rings.length - index
    }
  })

  return {
    tooltip: {
      show: true,
      trigger: 'item',
      triggerOn: 'mousemove',
      appendTo: 'body',
      backgroundColor: dark ? 'rgba(29, 29, 29, 0.96)' : 'rgba(255, 255, 255, 0.96)',
      borderColor: dark ? '#434343' : '#ebeef5',
      borderWidth: 1,
      textStyle: { color: dark ? '#e5eaf3' : '#303133', fontSize: 12 },
      extraCssText: 'box-shadow: 0 4px 12px rgba(0, 21, 41, 0.12); border-radius: 6px; z-index: 9999;'
    },
    series,
    graphic: [
      {
        type: 'group',
        left: 'center',
        top: 'middle',
        silent: true,
        z: 100,
        children: [
          {
            type: 'text',
            style: {
              text: centerTitle,
              fill: dark ? '#a3a6ad' : '#909399',
              fontSize: 13,
              fontWeight: 400,
              textAlign: 'center',
              textVerticalAlign: 'middle'
            },
            left: 'center',
            top: -14
          },
          {
            type: 'text',
            style: {
              text: centerValue,
              fill: dark ? '#60a5fa' : '#2f7cf6',
              fontSize: 28,
              fontWeight: 700,
              textAlign: 'center',
              textVerticalAlign: 'middle'
            },
            left: 'center',
            top: 12
          }
        ]
      }
    ]
  }
}
