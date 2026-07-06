<template>
  <div class="not-found">
    <el-result icon="warning" title="404" sub-title="页面不存在或已被移除">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { getHomePath } from '@/utils/home'

const router = useRouter()

function goHome() {
  router.push(getHomePath())
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push(getHomePath())
}
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 48px 16px;
}
</style>
