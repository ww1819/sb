<template>
  <div class="form-section">
    <div v-if="title" class="form-section__title">{{ title }}</div>
    <div class="form-section__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<style scoped>
.form-section {
  margin-bottom: 20px;
}
.form-section__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
  padding-bottom: 10px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--meis-border-light);
}
.form-section__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
