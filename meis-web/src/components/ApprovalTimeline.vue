<template>
  <el-timeline>
    <el-timeline-item v-for="r in records" :key="r.id" :timestamp="formatTime(r.acted_at)" placement="top">
      <el-card>
        <p><strong>{{ r.action }}</strong> — 节点 {{ r.node_order }}</p>
        <p v-if="r.comment">{{ r.comment }}</p>
      </el-card>
    </el-timeline-item>
    <el-timeline-item v-if="!records.length" timestamp="">暂无审批记录</el-timeline-item>
  </el-timeline>
  <div v-if="showActions" class="actions">
    <el-button type="success" @click="$emit('approve')">通过</el-button>
    <el-button type="danger" @click="$emit('reject')">驳回</el-button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ records: Record<string, unknown>[]; showActions?: boolean }>()
defineEmits<{ approve: []; reject: [] }>()

function formatTime(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}
</script>

<style scoped>
.actions { margin-top: 12px; display: flex; gap: 8px; }
</style>
