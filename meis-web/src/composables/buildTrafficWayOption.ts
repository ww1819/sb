import type { EChartsOption } from 'echarts'
import { TRAFFIC_WAY_CENTER_IMG } from '@/assets/dashboard/trafficWayCenterImg'

/** 示意数据：火车 / 飞机 / 客车 / 轮渡（DASH-UI-05，仅前端） */
const TRAFFIC_WAY = [
  { name: '火车', value: 20 },
  { name: '飞机', value: 10 },
  { name: '客车', value: 30 },
  { name: '轮渡', value: 40 }
] as const

const COLORS = ['#00cfff', '#006ced', '#3b82f6', '#f59e0b', '#ffa800', '#ff5b00', '#ff3000']

export function buildTrafficWayOption(): EChartsOption {
  const total = TRAFFIC_WAY.reduce((sum, item) => sum + item.value, 0)
  const data: Record<string, unknown>[] = []

  TRAFFIC_WAY.forEach((item, i) => {
    const color = COLORS[i]
    data.push({
      value: item.value,
      name: item.name,
      itemStyle: {
        color,
        borderWidth: 5,
        shadowBlur: 12,
        borderColor: color,
        shadowColor: color
      }
    })
    // 扇区间隙（透明，不参与交互）
    data.push({
      value: 2,
      name: '',
      tooltip: { show: false },
      itemStyle: {
        color: 'rgba(0, 0, 0, 0)',
        borderColor: 'rgba(0, 0, 0, 0)',
        borderWidth: 0
      },
      label: { show: false },
      labelLine: { show: false },
      emphasis: { disabled: true },
      select: { disabled: true }
    })
  })

  return {
    backgroundColor: 'transparent',
    color: COLORS,
    title: {
      text: '交通方式',
      top: '48%',
      textAlign: 'center',
      left: '49%',
      textStyle: {
        color: '#1f2937',
        fontSize: 22,
        fontWeight: 400
      }
    },
    graphic: {
      elements: [
        {
          type: 'image',
          z: 3,
          style: {
            image: TRAFFIC_WAY_CENTER_IMG,
            width: 178,
            height: 178
          },
          left: 'center',
          top: 'center',
          silent: true
        }
      ]
    },
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: unknown) => {
        const p = params as { name?: string; value?: number | string; percent?: number }
        if (!p?.name) return ''
        const value = Number(p.value) || 0
        const percent = total ? ((value / total) * 100).toFixed(0) : '0'
        return `${p.name}<br/>数量：${value}<br/>占比：${percent}%`
      }
    },
    legend: {
      icon: 'circle',
      orient: 'vertical',
      data: TRAFFIC_WAY.map((d) => d.name),
      right: 24,
      top: 'middle',
      align: 'left',
      textStyle: { color: '#374151' },
      itemGap: 16
    },
    toolbox: { show: false },
    series: [
      {
        name: '交通方式',
        type: 'pie',
        clockWise: false,
        radius: ['42%', '44%'],
        avoidLabelOverlap: true,
        label: {
          show: true,
          position: 'outside',
          color: '#4b5563',
          formatter: (params: { name?: string; value?: number | string }) => {
            if (!params.name) return ''
            const value = Number(params.value) || 0
            const percent = total ? ((value / total) * 100).toFixed(0) : '0'
            return `交通方式：${params.name}\n\n占百分比：${percent}%`
          }
        },
        labelLine: {
          length: 20,
          length2: 40,
          show: true,
          lineStyle: { color: '#94a3b8' }
        },
        emphasis: {
          scale: true,
          scaleSize: 10,
          itemStyle: {
            shadowBlur: 28,
            shadowOffsetX: 0,
            shadowOffsetY: 0
          },
          label: {
            show: true,
            fontWeight: 600,
            color: '#111827'
          }
        },
        data
      }
    ]
  }
}
