<template>
  <div class="device-image-panel">
    <div class="device-image-panel__toolbar">
      <div class="device-image-panel__filter">
        <span class="device-image-panel__filter-label">图片名称</span>
        <el-input v-model="keyword" placeholder="请输入图片名称" clearable class="device-image-panel__input" />
        <el-button type="primary">搜索</el-button>
      </div>
      <div class="device-image-panel__actions">
        <el-button type="primary" plain>选择图片</el-button>
        <el-button type="primary">上传</el-button>
        <el-button type="warning" plain>选中下载</el-button>
        <el-button type="danger" plain>选中删除</el-button>
        <el-button type="success" plain>设为主图</el-button>
      </div>
    </div>

    <div class="device-image-panel__body">
      <div class="device-image-panel__grid">
        <PageEmpty description="暂无设备图片" :image-size="72" />
      </div>
      <div class="device-image-panel__preview">
        <div class="device-image-panel__preview-box">
          <el-icon :size="42" color="#c0c4cc"><Picture /></el-icon>
          <p>请从左侧列表选择图片进行预览</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import PageEmpty from '@/components/table/PageEmpty.vue'

const keyword = ref('')
</script>

<style scoped>
.device-image-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 360px;
}

.device-image-panel__toolbar {
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

.device-image-panel__filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-image-panel__filter-label {
  font-size: 13px;
  color: var(--meis-text-secondary);
  white-space: nowrap;
}

.device-image-panel__input {
  width: 220px;
}

.device-image-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.device-image-panel__body {
  flex: 1;
  min-height: 320px;
  display: flex;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-card-radius);
  overflow: hidden;
  background: #fff;
}

.device-image-panel__grid {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--meis-border-light);
  background: #fafbfc;
}

.device-image-panel__preview {
  width: 42%;
  min-width: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #fcfcfd;
}

.device-image-panel__preview-box {
  width: 100%;
  padding: 56px 24px;
  text-align: center;
  border: 1px dashed #dcdfe6;
  border-radius: var(--meis-card-radius);
  background: #fff;
}

.device-image-panel__preview-box p {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--meis-text-secondary);
}

@media (max-width: 1100px) {
  .device-image-panel__body {
    flex-direction: column;
  }

  .device-image-panel__grid {
    border-right: none;
    border-bottom: 1px solid var(--meis-border-light);
    min-height: 180px;
  }

  .device-image-panel__preview {
    width: 100%;
    min-width: 0;
  }
}
</style>
