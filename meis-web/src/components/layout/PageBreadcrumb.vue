<template>
  <nav v-if="items.length" class="page-breadcrumb" aria-label="面包屑">
    <template v-for="(item, index) in items" :key="`${item.label}-${index}`">
      <span v-if="index > 0" class="sep">/</span>
      <span class="crumb" :class="{ current: index === items.length - 1 }">{{ item.label }}</span>
    </template>
  </nav>
</template>

<script setup lang="ts">
import type { BreadcrumbItem } from '@/utils/menuNav'

defineProps<{
  items: BreadcrumbItem[]
}>()
</script>

<style scoped>
.page-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
  font-size: var(--meis-font-subtitle);
  color: var(--meis-text-secondary);
}

.sep {
  color: var(--meis-text-secondary);
  opacity: 0.6;
}

.crumb {
  line-height: 1.5;
}

.crumb.current {
  color: var(--meis-text-primary);
  font-weight: 600;
}
</style>
