<template>
  <nav class="form-tab-nav" role="tablist">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      type="button"
      role="tab"
      class="form-tab-nav__item"
      :class="{ 'is-active': modelValue === tab.key }"
      :aria-selected="modelValue === tab.key"
      @click="$emit('update:modelValue', tab.key)"
    >
      {{ tab.label }}
    </button>
  </nav>
</template>

<script setup lang="ts">
export interface FormTabItem {
  key: string
  label: string
}

defineProps<{
  modelValue: string
  tabs: FormTabItem[]
}>()

defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<style scoped>
.form-tab-nav {
  display: flex;
  align-items: center;
  gap: 28px;
  margin: 0 0 16px;
  padding: 0 4px;
  border-bottom: 1px solid var(--meis-border-light);
  overflow-x: auto;
  flex-shrink: 0;
}

.form-tab-nav::-webkit-scrollbar {
  height: 4px;
}

.form-tab-nav__item {
  position: relative;
  margin: 0;
  padding: 10px 2px 12px;
  border: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.4;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.18s ease;
}

.form-tab-nav__item:hover {
  color: #303133;
}

.form-tab-nav__item.is-active {
  color: var(--el-color-primary);
  font-weight: 500;
}

.form-tab-nav__item.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--el-color-primary);
  border-radius: 2px 2px 0 0;
}
</style>
