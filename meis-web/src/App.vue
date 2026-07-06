<template>
  <el-config-provider :locale="zhCn" size="default">
    <RouteProgressBar />
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import RouteProgressBar from '@/components/layout/RouteProgressBar.vue'
</script>
