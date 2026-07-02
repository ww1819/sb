<template>
  <div class="approval-panel">
    <ApprovalTimeline :records="records" :show-actions="showActions" @approve="onApprove" @reject="onReject" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import ApprovalTimeline from './ApprovalTimeline.vue'

const props = defineProps<{ businessType: string; businessId: string }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const records = ref<Record<string, unknown>[]>([])
const instance = ref<Record<string, unknown> | null>(null)
const showActions = ref(false)

async function load() {
  if (!props.businessId) return
  const { data: instRes } = await http.get('/system/approval/business', {
    params: { businessType: props.businessType, businessId: props.businessId }
  })
  instance.value = instRes.data
  if (instance.value?.id) {
    const { data } = await http.get(`/system/approval/instance/${instance.value.id}/records`)
    records.value = data.data ?? []
    showActions.value = instance.value.status === 'pending'
  }
}

async function onApprove() {
  await http.post(`/system/approval/${instance.value!.id}/approve`, {
    approverId: auth.user?.userId,
    comment: '同意'
  })
  emit('changed')
  load()
}

async function onReject() {
  await http.post(`/system/approval/${instance.value!.id}/reject`, {
    approverId: auth.user?.userId,
    comment: '驳回'
  })
  emit('changed')
  load()
}

watch(() => props.businessId, load, { immediate: true })
onMounted(load)
</script>
