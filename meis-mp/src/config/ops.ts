export type OpsModule = 'maintain' | 'inspect' | 'pm'

export interface OpsModuleConfig {
  title: string
  module: OpsModule
  executionBase: string
  planDuePath: string
  templateListPath: string
  levelListPath: string
  levelField: string
  levelIdField: string
  levelLabel: string
  levelOptionLabelKey: string
  levelOptionCodeKey: string
}

export const OPS_MODULES: Record<OpsModule, OpsModuleConfig> = {
  maintain: {
    title: '保养执行',
    module: 'maintain',
    executionBase: '/maintain/execution',
    planDuePath: '/maintain/plan/due',
    templateListPath: '/maintain/maintenance_template/list',
    levelListPath: '/maintain/maintenance_level/list',
    levelField: 'maintenance_level',
    levelIdField: 'maintenance_level_id',
    levelLabel: '保养级别',
    levelOptionLabelKey: 'level_name',
    levelOptionCodeKey: 'level_code'
  },
  inspect: {
    title: '巡检执行',
    module: 'inspect',
    executionBase: '/inspect/execution',
    planDuePath: '/inspect/plan/due',
    templateListPath: '/inspect/inspection_template/list',
    levelListPath: '/inspect/inspection_type/list',
    levelField: 'inspection_type',
    levelIdField: 'inspection_type_id',
    levelLabel: '巡检类型',
    levelOptionLabelKey: 'type_name',
    levelOptionCodeKey: 'type_code'
  },
  pm: {
    title: '预防性维护',
    module: 'pm',
    executionBase: '/pm/execution',
    planDuePath: '/pm/plan/due',
    templateListPath: '/maintain/pm_template/list',
    levelListPath: '/maintain/pm_type/list',
    levelField: 'pm_type',
    levelIdField: 'pm_type_id',
    levelLabel: 'PM 类型',
    levelOptionLabelKey: 'type_name',
    levelOptionCodeKey: 'type_code'
  }
}
