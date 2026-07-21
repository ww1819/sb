export type OpsModule = 'maintain' | 'inspect' | 'pm'

export interface OpsModuleConfig {
  title: string
  module: OpsModule
  executionBase: string
  planDuePath: string
  templateListPath: string
  levelField: string
  levelLabel: string
}

export const OPS_MODULES: Record<OpsModule, OpsModuleConfig> = {
  maintain: {
    title: '保养执行',
    module: 'maintain',
    executionBase: '/maintain/execution',
    planDuePath: '/maintain/plan/due',
    templateListPath: '/maintain/maintenance_template/list',
    levelField: 'maintenance_level',
    levelLabel: '保养级别'
  },
  inspect: {
    title: '巡检执行',
    module: 'inspect',
    executionBase: '/inspect/execution',
    planDuePath: '/inspect/plan/due',
    templateListPath: '/inspect/inspection_template/list',
    levelField: 'inspection_type',
    levelLabel: '巡检类型'
  },
  pm: {
    title: '预防性维护',
    module: 'pm',
    executionBase: '/pm/execution',
    planDuePath: '/pm/plan/due',
    templateListPath: '/maintain/pm_template/list',
    levelField: 'pm_type',
    levelLabel: 'PM 类型'
  }
}
