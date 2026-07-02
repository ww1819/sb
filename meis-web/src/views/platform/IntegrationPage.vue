<template>
  <div>
    <el-table :data="adapters" border>
      <el-table-column prop="code" label="系统" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link @click="sync(row.code)">同步</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-table :data="tasks" border class="mt">
      <el-table-column prop="system_code" label="系统" />
      <el-table-column prop="task_type" label="类型" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="created_at" label="时间" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const adapters = ref<Record<string, string>[]>([])
const tasks = ref<Record<string, unknown>[]>([])

onMounted(load)

async function load() {
  const [a, t] = await Promise.all([http.get('/integration/adapters'), http.get('/integration/tasks')])
  adapters.value = a.data.data ?? []
  tasks.value = t.data.data ?? []
}

async function sync(code: string) {
  await http.post(`/integration/${code}/sync`, { taskType: 'manual' })
  load()
}
</script>

<style scoped>
.mt { margin-top: 16px; }
</style>
