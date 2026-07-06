<template>
  <div class="route-progress-track" :class="{ active: store.visible }">
    <div class="route-progress-bar" :style="{ width: `${store.progress}%` }" />
  </div>
</template>

<script setup lang="ts">
import { useRouteProgressStore } from '@/stores/routeProgress'

const store = useRouteProgressStore()
</script>

<style scoped>
.route-progress-track {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 9999;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.route-progress-track.active {
  opacity: 1;
}

.route-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary));
  box-shadow: 0 0 8px rgba(22, 119, 255, 0.45);
  transition: width 0.22s ease;
}
</style>
