import type { EChartsOption } from 'echarts'
import { CHART_COLORS } from '@/composables/useChartTheme'

export interface DeptCountItem {
  dept_name: string
  count: number
}

const TOP_N = 10

/** 降序 TOP N，其余合并为「其他」 */
export function mergeTopDeptCounts(items: DeptCountItem[], topN = TOP_N): DeptCountItem[] {
  const sorted = [...items]
    .map((i) => ({
      dept_name: i.dept_name || '未分配',
      count: Number(i.count) || 0
    }))
    .filter((i) => i.count > 0)
    .sort((a, b) => b.count - a.count)

  if (sorted.length <= topN) return sorted

  const top = sorted.slice(0, topN)
  const rest = sorted.slice(topN).reduce((sum, i) => sum + i.count, 0)
  if (rest > 0) top.push({ dept_name: '其他', count: rest })
  return top
}

export function buildAssetDistributionOption(raw: DeptCountItem[]): EChartsOption {
  const items = mergeTopDeptCounts(raw)
  const total = items.reduce((sum, i) => sum + i.count, 0)
  const data = items.map((i) => ({ name: i.dept_name, value: i.count }))

  return {
    backgroundColor: 'transparent',
    color: CHART_COLORS,
    title: {
      text: '资产分布',
      left: '34%',
      top: '46%',
      textAlign: 'center',
      textStyle: {
        color: '#1f2937',
        fontSize: 16,
        fontWeight: 600
      },
      subtext: total ? `共 ${total} 台` : '',
      subtextStyle: {
        color: '#909399',
        fontSize: 12
      }
    },
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: unknown) => {
        const p = params as { name?: string; value?: number | string; percent?: number }
        if (!p?.name) return ''
        const value = Number(p.value) || 0
        const percent =
          p.percent != null
            ? Number(p.percent).toFixed(1)
            : total
              ? ((value / total) * 100).toFixed(1)
              : '0.0'
        return `${p.name}<br/>数量：${value}<br/>占比：${percent}%`
      }
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 16,
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 12,
      textStyle: { color: '#606266', fontSize: 12 },
      formatter: (name: string) => {
        const row = items.find((i) => i.dept_name === name)
        if (!row || !total) return name
        const pct = ((row.count / total) * 100).toFixed(1)
        return `${name}  ${row.count}（${pct}%）`
      }
    },
    series: [
      {
        name: '资产分布',
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          color: '#4b5563',
          formatter: (params: { name?: string; value?: number | string; percent?: number }) => {
            if (!params.name) return ''
            const value = Number(params.value) || 0
            const percent =
              params.percent != null
                ? Number(params.percent).toFixed(1)
                : total
                  ? ((value / total) * 100).toFixed(1)
                  : '0.0'
            return `${params.name}\n${value}台 · ${percent}%`
          }
        },
        labelLine: {
          length: 12,
          length2: 10,
          lineStyle: { color: '#94a3b8' }
        },
        emphasis: {
          scale: true,
          scaleSize: 8,
          itemStyle: {
            shadowBlur: 16,
            shadowColor: 'rgba(0,0,0,0.12)'
          },
          label: {
            fontWeight: 600,
            color: '#111827'
          }
        },
        data
      }
    ]
  }
}
