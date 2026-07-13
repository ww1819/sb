import type { Component } from 'vue'
import {
  Box,
  Calendar,
  DocumentChecked,
  List,
  Monitor,
  ShoppingCart,
  Tools,
  WarningFilled
} from '@element-plus/icons-vue'

export type DashboardProfile = 'admin' | 'asset' | 'repair' | 'purchase'

export type DashboardKpiKey =
  | 'deviceCount'
  | 'openWorkorders'
  | 'activeMaintenancePlans'
  | 'pendingApprovals'

export type DashboardChartKey = 'trend' | 'brand' | 'status' | 'origin' | 'newDevice' | 'deptValue'

export interface DashboardKpiConfig {
  key: DashboardKpiKey
  title: string
  hint: string
  icon: Component
  color: string
  bgColor: string
}

export interface DashboardProfileConfig {
  id: DashboardProfile
  title: string
  subtitle: string
  kpis: DashboardKpiConfig[]
  quickPaths: string[]
  charts: DashboardChartKey[]
  showTodos: boolean
  showMessages: boolean
}

const KPI: Record<DashboardKpiKey, Omit<DashboardKpiConfig, 'key'>> = {
  deviceCount: {
    title: '设备总数',
    hint: '全院在管设备',
    icon: Monitor,
    color: '#1677ff',
    bgColor: 'rgba(22, 119, 255, 0.08)'
  },
  openWorkorders: {
    title: '待处理工单',
    hint: '需及时跟进',
    icon: Tools,
    color: '#fa8c16',
    bgColor: 'rgba(250, 140, 22, 0.08)'
  },
  activeMaintenancePlans: {
    title: '保养计划',
    hint: '执行中计划',
    icon: Calendar,
    color: '#13c2c2',
    bgColor: 'rgba(19, 194, 194, 0.08)'
  },
  pendingApprovals: {
    title: '待审批',
    hint: '待处理流程',
    icon: DocumentChecked,
    color: '#722ed1',
    bgColor: 'rgba(114, 46, 209, 0.08)'
  }
}

function kpi(...keys: DashboardKpiKey[]): DashboardKpiConfig[] {
  return keys.map((key) => ({ key, ...KPI[key] }))
}

export const DASHBOARD_PROFILES: Record<DashboardProfile, DashboardProfileConfig> = {
  admin: {
    id: 'admin',
    title: '管理工作台',
    subtitle: '全院设备运营概览',
    kpis: kpi('deviceCount', 'openWorkorders', 'activeMaintenancePlans', 'pendingApprovals'),
    quickPaths: ['/repair/workorder', '/asset/outbound', '/asset/inventory', '/purchase/plan'],
    charts: ['trend', 'brand', 'status', 'origin', 'newDevice'],
    showTodos: true,
    showMessages: true
  },
  asset: {
    id: 'asset',
    title: '资产管理工作台',
    subtitle: '设备台账、盘点与流转',
    kpis: kpi('deviceCount', 'openWorkorders', 'activeMaintenancePlans'),
    quickPaths: ['/asset/device', '/asset/outbound', '/asset/inventory', '/asset/transfer'],
    charts: ['status', 'brand', 'deptValue', 'newDevice'],
    showTodos: true,
    showMessages: true
  },
  repair: {
    id: 'repair',
    title: '维修工程师工作台',
    subtitle: '工单响应与保养执行',
    kpis: kpi('openWorkorders', 'activeMaintenancePlans', 'deviceCount'),
    quickPaths: ['/repair/apply', '/repair/handle', '/repair/verify', '/repair/spare-archive'],
    charts: ['trend', 'status'],
    showTodos: true,
    showMessages: true
  },
  purchase: {
    id: 'purchase',
    title: '采购管理工作台',
    subtitle: '计划编制与审批跟进',
    kpis: kpi('pendingApprovals', 'deviceCount', 'openWorkorders'),
    quickPaths: ['/purchase/plan', '/purchase/project', '/purchase/contract', '/asset/device'],
    charts: ['newDevice', 'brand'],
    showTodos: true,
    showMessages: false
  }
}

export const ALL_QUICK_ENTRIES = [
  {
    label: '快速报修',
    desc: '提交设备故障工单',
    path: '/repair/apply',
    icon: WarningFilled,
    color: '#fa541c',
    bgColor: 'rgba(250, 84, 28, 0.08)'
  },
  {
    label: '设备领用',
    desc: '办理出库与领用',
    path: '/asset/outbound',
    icon: Box,
    color: '#1677ff',
    bgColor: 'rgba(22, 119, 255, 0.08)'
  },
  {
    label: '资产盘点',
    desc: '发起盘点任务',
    path: '/asset/inventory',
    icon: List,
    color: '#13c2c2',
    bgColor: 'rgba(19, 194, 194, 0.08)'
  },
  {
    label: '采购计划',
    desc: '编制采购预算',
    path: '/purchase/plan',
    icon: ShoppingCart,
    color: '#722ed1',
    bgColor: 'rgba(114, 46, 209, 0.08)'
  },
  {
    label: '设备台账',
    desc: '查看设备档案',
    path: '/asset/device',
    icon: Monitor,
    color: '#1677ff',
    bgColor: 'rgba(22, 119, 255, 0.08)'
  },
  {
    label: '资产流转',
    desc: '科室间调拨',
    path: '/asset/transfer',
    icon: Box,
    color: '#13c2c2',
    bgColor: 'rgba(19, 194, 194, 0.08)'
  },
  {
    label: '保养计划',
    desc: '查看保养安排',
    path: '/maintain/plan',
    icon: Calendar,
    color: '#13c2c2',
    bgColor: 'rgba(19, 194, 194, 0.08)'
  },
  {
    label: '保养记录',
    desc: '登记保养结果',
    path: '/maintain/record',
    icon: Calendar,
    color: '#52c41a',
    bgColor: 'rgba(82, 196, 26, 0.08)'
  },
  {
    label: '采购项目',
    desc: '项目管理',
    path: '/purchase/project',
    icon: ShoppingCart,
    color: '#722ed1',
    bgColor: 'rgba(114, 46, 209, 0.08)'
  },
  {
    label: '采购合同',
    desc: '合同管理',
    path: '/purchase/contract',
    icon: DocumentChecked,
    color: '#fa8c16',
    bgColor: 'rgba(250, 140, 22, 0.08)'
  }
]

export function resolveDashboardProfile(roles: string[] | undefined): DashboardProfile {
  const set = new Set((roles ?? []).map((r) => r.toLowerCase()))
  if (set.has('admin') || set.has('tenant_admin') || set.has('equipment_head') || set.has('hospital_leader')) {
    return 'admin'
  }
  if (set.has('purchase_staff')) return 'purchase'
  if (set.has('engineer') || set.has('clinical_user')) return 'repair'
  if (set.has('dept_admin') || set.has('warehouse_keeper')) return 'asset'
  return 'admin'
}
