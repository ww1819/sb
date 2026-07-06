<template>
  <div class="feed-list" :style="maxHeight ? { maxHeight, overflow: 'auto' } : undefined">
    <template v-if="displayItems.length">
      <div
        v-for="(item, index) in displayItems"
        :key="itemKey(item, index)"
        class="feed-item"
        :class="{ 'feed-item--unread': isUnread(item) }"
      >
        <span class="feed-dot" :style="{ background: dotColor(item) }" />
        <div class="feed-body">
          <div class="feed-title">{{ itemTitle(item) }}</div>
          <div class="feed-meta">
            <span class="feed-type">{{ typeLabel(item) }}</span>
            <span v-if="timeField && item[timeField]" class="feed-time">{{ formatTime(item[timeField]) }}</span>
          </div>
        </div>
        <span v-if="isUnread(item)" class="feed-unread-dot" />
      </div>
    </template>
    <PageEmpty v-else :description="emptyText" :image-size="72" />

    <button v-if="showMoreButton" type="button" class="feed-more" @click="expanded = true">
      查看全部（{{ items.length }}）
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import PageEmpty from '@/components/table/PageEmpty.vue'

const props = withDefaults(
  defineProps<{
    items: Record<string, unknown>[]
    typeField: string
    timeField?: string
    unreadField?: string
    limit?: number
    emptyText?: string
    maxHeight?: string
  }>(),
  {
    timeField: 'created_at',
    unreadField: 'is_read',
    limit: 5,
    emptyText: '暂无数据'
  }
)

const expanded = ref(false)

const displayItems = computed(() => {
  if (expanded.value) return props.items
  return props.items.slice(0, props.limit)
})

const showMoreButton = computed(() => !expanded.value && props.items.length > props.limit)

const TYPE_COLORS: Record<string, string> = {
  approval: '#722ed1',
  workorder: '#fa541c',
  repair: '#fa541c',
  maintenance: '#13c2c2',
  purchase: '#1677ff',
  system: '#1677ff',
  alert: '#ff4d4f',
  notice: '#52c41a'
}

function itemKey(item: Record<string, unknown>, index: number) {
  return String(item.id ?? `${item.title}-${index}`)
}

function itemTitle(item: Record<string, unknown>) {
  return String(item.title ?? item.wo_no ?? '-')
}

function typeLabel(item: Record<string, unknown>) {
  const raw = item[props.typeField]
  if (!raw) return '其他'
  const map: Record<string, string> = {
    approval: '审批',
    workorder: '工单',
    repair: '维修',
    system: '系统',
    alert: '告警',
    notice: '通知'
  }
  const key = String(raw).toLowerCase()
  return map[key] ?? String(raw)
}

function dotColor(item: Record<string, unknown>) {
  const key = String(item[props.typeField] ?? '').toLowerCase()
  return TYPE_COLORS[key] ?? '#909399'
}

function isUnread(item: Record<string, unknown>) {
  if (!props.unreadField) return false
  const val = item[props.unreadField]
  return val === false || val === 'false' || val === 0 || val === '0'
}

function formatTime(value: unknown) {
  if (!value) return ''
  const text = String(value)
  return text.length > 16 ? text.slice(0, 16) : text
}
</script>

<style scoped>
.feed-list {
  min-height: 120px;
}

.feed-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--meis-border-light);
  transition: background 0.15s ease;
}

.feed-item:last-child {
  border-bottom: none;
}

.feed-item:hover {
  background: var(--meis-table-hover);
  border-radius: var(--meis-card-radius);
}

.feed-item--unread .feed-title {
  font-weight: 600;
  color: var(--meis-text-primary);
}

.feed-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
}

.feed-body {
  flex: 1;
  min-width: 0;
}

.feed-title {
  font-size: var(--meis-font-subtitle);
  color: var(--meis-text-primary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.feed-type {
  font-size: var(--meis-font-caption);
  color: var(--meis-text-secondary);
}

.feed-time {
  font-size: var(--meis-font-caption);
  color: var(--meis-text-secondary);
}

.feed-unread-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--meis-status-info);
}

.feed-more {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  font-size: var(--meis-font-caption);
  cursor: pointer;
  text-align: center;
}

.feed-more:hover {
  color: var(--el-color-primary-dark-2);
}
</style>
