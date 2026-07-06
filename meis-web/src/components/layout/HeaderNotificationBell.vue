<template>
  <el-dropdown trigger="click" popper-class="notification-popper" @visible-change="onVisibleChange">
    <button type="button" class="bell-btn" :class="{ dark }" aria-label="消息通知">
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-icon :size="18"><Bell /></el-icon>
      </el-badge>
    </button>
    <template #dropdown>
      <div class="notification-panel">
        <div class="panel-head">
          <span class="panel-title">消息通知</span>
          <span v-if="unreadCount" class="panel-count">{{ unreadCount }} 条未读</span>
        </div>
        <div v-if="loading" class="panel-loading">加载中...</div>
        <template v-else-if="messages.length">
          <div
            v-for="msg in previewMessages"
            :key="String(msg.id)"
            class="msg-item"
            :class="{ unread: isUnread(msg) }"
            @click="onMessageClick(msg)"
          >
            <div class="msg-title">{{ msg.title || '系统消息' }}</div>
            <div class="msg-meta">
              <span>{{ typeLabel(msg.message_type) }}</span>
              <span>{{ formatTime(msg.created_at) }}</span>
            </div>
          </div>
        </template>
        <div v-else class="panel-empty">暂无消息</div>
        <div class="panel-foot">
          <el-button link type="primary" @click="emit('view-all')">查看全部</el-button>
        </div>
      </div>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import http from '@/api/http'

defineProps<{
  dark?: boolean
}>()

const emit = defineEmits<{
  'view-all': []
}>()

const loading = ref(false)
const messages = ref<Record<string, unknown>[]>([])

const unreadCount = computed(
  () => messages.value.filter((m) => isUnread(m)).length
)

const previewMessages = computed(() => messages.value.slice(0, 8))

function isUnread(msg: Record<string, unknown>) {
  const val = msg.is_read
  return val === false || val === 'false' || val === 0 || val === '0'
}

function typeLabel(type: unknown) {
  const map: Record<string, string> = {
    system: '系统',
    warranty_due: '保修',
    alert: '告警'
  }
  const key = String(type ?? 'system').toLowerCase()
  return map[key] ?? String(type ?? '通知')
}

function formatTime(value: unknown) {
  if (!value) return ''
  const text = String(value)
  return text.length > 16 ? text.slice(0, 16) : text
}

async function loadMessages() {
  loading.value = true
  try {
    const { data } = await http.get('/notification/messages')
    messages.value = data.data ?? []
  } catch {
    messages.value = []
  } finally {
    loading.value = false
  }
}

async function onMessageClick(msg: Record<string, unknown>) {
  if (!isUnread(msg) || !msg.id) return
  try {
    await http.post(`/notification/messages/${msg.id}/read`)
    msg.is_read = true
  } catch {
    // ignore
  }
}

function onVisibleChange(visible: boolean) {
  if (visible) loadMessages()
}

onMounted(loadMessages)
</script>

<style scoped>
.bell-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--meis-text-primary);
  transition: background 0.15s ease;
}

.bell-btn.dark {
  color: rgba(255, 255, 255, 0.9);
}

.bell-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.bell-btn.dark:hover {
  background: rgba(255, 255, 255, 0.12);
}

.notification-panel {
  width: 320px;
  padding: 4px 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px 12px;
  border-bottom: 1px solid var(--meis-border-light);
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.panel-count {
  font-size: 12px;
  color: var(--el-color-primary);
}

.panel-loading,
.panel-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--meis-text-secondary);
}

.msg-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--meis-border-light);
  transition: background 0.15s ease;
}

.msg-item:last-of-type {
  border-bottom: none;
}

.msg-item:hover {
  background: var(--meis-table-hover);
}

.msg-item.unread .msg-title {
  font-weight: 600;
  color: var(--meis-text-primary);
}

.msg-title {
  font-size: 13px;
  color: var(--meis-text-primary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--meis-text-secondary);
}

.panel-foot {
  padding: 8px 16px 4px;
  text-align: center;
  border-top: 1px solid var(--meis-border-light);
}
</style>
