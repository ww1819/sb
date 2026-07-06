<template>
  <div
    v-if="tabsStore.tabs.length"
    class="tab-bar"
    @contextmenu.prevent
  >
    <div
      v-for="tab in tabsStore.tabs"
      :key="tab.path"
      class="tab-item"
      :class="{ active: tab.path === tabsStore.activePath }"
      :title="tab.title"
      @click="tabsStore.switchTo(tab.path)"
      @contextmenu.prevent="openContextMenu($event, tab)"
    >
      <span class="tab-title">{{ tab.title }}</span>
      <el-icon
        v-if="tab.closable"
        class="tab-close"
        @click.stop="tabsStore.close(tab.path)"
      >
        <Close />
      </el-icon>
    </div>

    <ul
      v-show="contextMenu.visible"
      class="tab-context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
    >
      <li @click="handleCloseCurrent">关闭当前</li>
      <li @click="handleCloseOthers">关闭其他</li>
      <li @click="handleCloseAll">关闭全部</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { useTabsStore, type TabItem } from '@/stores/tabs'

const tabsStore = useTabsStore()
const contextMenu = reactive({ visible: false, x: 0, y: 0, path: '' })

function openContextMenu(e: MouseEvent, tab: TabItem) {
  contextMenu.visible = true
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.path = tab.path
}

function hideContextMenu() {
  contextMenu.visible = false
}

function handleCloseCurrent() {
  if (contextMenu.path) tabsStore.close(contextMenu.path)
  hideContextMenu()
}

function handleCloseOthers() {
  if (contextMenu.path) tabsStore.closeOthers(contextMenu.path)
  hideContextMenu()
}

function handleCloseAll() {
  tabsStore.closeAll()
  hideContextMenu()
}

onMounted(() => document.addEventListener('click', hideContextMenu))
onUnmounted(() => document.removeEventListener('click', hideContextMenu))
</script>

<style scoped>
.tab-bar {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 4px 16px 0;
  background: #e8ecf1;
  border-bottom: 1px solid var(--meis-border-light);
  overflow-x: auto;
  flex-shrink: 0;
}

.tab-bar::-webkit-scrollbar {
  height: 4px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 180px;
  padding: 8px 14px;
  background: #dce3ec;
  border: 1px solid #d0d7e2;
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  user-select: none;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
  flex-shrink: 0;
}

.tab-item:hover {
  background: #eef2f7;
  color: #303133;
}

.tab-item.active {
  background: var(--meis-page-bg);
  color: var(--el-color-primary);
  font-weight: 500;
  padding-bottom: 9px;
  margin-bottom: -1px;
  z-index: 1;
  border-color: var(--meis-border-light);
  transform: translateY(-1px);
}

.tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-close {
  font-size: 12px;
  border-radius: 50%;
  padding: 2px;
  flex-shrink: 0;
}

.tab-close:hover {
  background: rgba(0, 0, 0, 0.08);
  color: #f56c6c;
}

.tab-context-menu {
  position: fixed;
  z-index: 3000;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: #fff;
  border: 1px solid var(--meis-border-light);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  min-width: 120px;
}

.tab-context-menu li {
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
}

.tab-context-menu li:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
</style>
