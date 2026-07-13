import type { ListFilter } from './pageRegistry'

/** 报修申请：未删全量，状态可多选 */
export const REPAIR_APPLY_FILTERS: ListFilter[] = [
  {
    key: 'statuses',
    label: '状态',
    dictType: 'wo_status',
    multiple: true,
    dictValues: [
      'draft',
      'reported',
      'dispatching',
      'pending_accept',
      'accepted',
      'repairing',
      'pending_verify',
      'verify_rejected',
      'verified',
      'closed',
      'cancelled',
      'suspended'
    ]
  },
  { key: 'urgencyLevel', label: '紧急程度', dictType: 'urgency' },
  { key: 'reportDeptId', label: '报修科室', linkTable: 'department' },
  { key: 'assignedUserId', label: '负责人', linkTable: 'repair_engineer' },
  {
    key: 'assignment',
    label: '指派',
    options: [
      { value: 'unassigned', label: '未指派' },
      { value: 'assigned', label: '已指派' }
    ]
  },
  { key: 'reportTime', label: '报修时间', type: 'daterange' }
]

/** 维修处理：默认范围见 mode=handle；状态筛选为子集多选 */
export const REPAIR_HANDLE_FILTERS: ListFilter[] = [
  {
    key: 'statuses',
    label: '状态',
    dictType: 'wo_status',
    multiple: true,
    dictValues: [
      'reported',
      'dispatching',
      'pending_accept',
      'accepted',
      'repairing',
      'suspended',
      'verify_rejected',
      'pending_verify',
      'verified'
    ]
  },
  { key: 'urgencyLevel', label: '紧急程度', dictType: 'urgency' },
  { key: 'reportDeptId', label: '报修科室', linkTable: 'department' },
  { key: 'assignedUserId', label: '负责人', linkTable: 'repair_engineer' },
  {
    key: 'assignment',
    label: '指派',
    options: [
      { value: 'unassigned', label: '未指派' },
      { value: 'assigned', label: '已指派' }
    ]
  },
  { key: 'reportTime', label: '报修时间', type: 'daterange' }
]

/** 维修验收 */
export const REPAIR_VERIFY_FILTERS: ListFilter[] = [
  {
    key: 'statuses',
    label: '状态',
    dictType: 'wo_status',
    multiple: true,
    dictValues: ['pending_verify', 'verified', 'closed']
  },
  { key: 'urgencyLevel', label: '紧急程度', dictType: 'urgency' },
  { key: 'reportDeptId', label: '报修科室', linkTable: 'department' },
  { key: 'assignedUserId', label: '负责人', linkTable: 'repair_engineer' },
  { key: 'reportTime', label: '报修时间', type: 'daterange' }
]
