<template>
  <div class="device-archive-panel">
    <div class="device-archive-panel__toolbar">
      <div class="device-archive-panel__filter">
        <span class="device-archive-panel__filter-label">文档名称</span>
        <el-input v-model="keyword" placeholder="请输入文档名称" clearable class="device-archive-panel__input" />
        <el-button type="primary">搜索</el-button>
      </div>
      <div class="device-archive-panel__actions">
        <el-button type="primary" plain>选择文件</el-button>
        <el-button type="primary">上传</el-button>
        <el-button type="warning" plain>选中下载</el-button>
        <el-button type="warning" plain>选中复制到...</el-button>
        <el-button type="danger" plain>选中删除</el-button>
        <el-button type="success" plain>预览图片</el-button>
      </div>
    </div>

    <div class="device-archive-panel__body">
      <aside class="device-archive-panel__sidebar">
        <div class="device-archive-panel__tree-title">
          <el-icon><Folder /></el-icon>
          <span>文件类型</span>
        </div>
        <ul class="device-archive-panel__tree">
          <li v-for="item in fileTypes" :key="item" class="is-active">{{ item }}</li>
        </ul>
      </aside>

      <div class="device-archive-panel__main">
        <div class="device-archive-panel__list">
          <PageEmpty description="没有数据" :image-size="72" />
        </div>
        <div class="device-archive-panel__preview">
          <p class="device-archive-panel__preview-hint">请从列表中选择图片进行预览</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Folder } from '@element-plus/icons-vue'
import PageEmpty from '@/components/table/PageEmpty.vue'

const keyword = ref('')
const fileTypes = ['全部文件', '合格证', '说明书', '验收资料', '其他资料']
</script>

<style scoped>
.device-archive-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 360px;
}

.device-archive-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  background: #fafbfc;
}

.device-archive-panel__filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-archive-panel__filter-label {
  font-size: 13px;
  color: var(--meis-text-secondary);
  white-space: nowrap;
}

.device-archive-panel__input {
  width: 220px;
}

.device-archive-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.device-archive-panel__body {
  flex: 1;
  min-height: 320px;
  display: flex;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  overflow: hidden;
  background: #fff;
}

.device-archive-panel__sidebar {
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid var(--meis-border-light);
  background: #fafbfc;
}

.device-archive-panel__tree-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--meis-text-primary);
  border-bottom: 1px solid var(--meis-border-light);
}

.device-archive-panel__tree {
  margin: 0;
  padding: 8px 0;
  list-style: none;
}

.device-archive-panel__tree li {
  padding: 8px 14px 8px 28px;
  font-size: 13px;
  color: var(--meis-text-secondary);
  cursor: pointer;
}

.device-archive-panel__tree li:hover,
.device-archive-panel__tree li.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.device-archive-panel__main {
  flex: 1;
  min-width: 0;
  display: flex;
}

.device-archive-panel__list {
  flex: 1;
  min-width: 0;
  border-right: 1px solid var(--meis-border-light);
  display: flex;
  align-items: center;
  justify-content: center;
}

.device-archive-panel__preview {
  width: 42%;
  min-width: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #fcfcfd;
}

.device-archive-panel__preview-hint {
  margin: 0;
  padding: 48px 24px;
  width: 100%;
  text-align: center;
  font-size: 13px;
  color: var(--meis-text-secondary);
  border: 1px dashed #dcdfe6;
  border-radius: var(--meis-card-radius);
  background: #fff;
}

@media (max-width: 1100px) {
  .device-archive-panel__main {
    flex-direction: column;
  }

  .device-archive-panel__list {
    border-right: none;
    border-bottom: 1px solid var(--meis-border-light);
    min-height: 180px;
  }

  .device-archive-panel__preview {
    width: 100%;
    min-width: 0;
  }
}
</style>
