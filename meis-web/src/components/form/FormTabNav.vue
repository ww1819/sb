<template>
  <nav
    class="form-tab-nav"
    :class="layout === 'side' ? 'form-tab-nav--side' : 'form-tab-nav--top'"
    role="tablist"
  >
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

withDefaults(
  defineProps<{
    modelValue: string
    tabs: FormTabItem[]
    /** top：顶栏横向；side：左侧纵向滚动（台账查看态） */
    layout?: 'top' | 'side'
  }>(),
  { layout: 'top' }
)

defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<style scoped>
.form-tab-nav--top {
  display: flex;
  align-items: center;
  gap: 28px;
  margin: 0 0 16px;
  padding: 0 4px;
  border-bottom: 1px solid var(--meis-border-light);
  overflow-x: auto;
  flex-shrink: 0;
}

.form-tab-nav--top::-webkit-scrollbar {
  height: 4px;
}

.form-tab-nav--side {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  width: 156px;
  flex-shrink: 0;
  margin: 0 12px 0 0;
  padding: 4px 8px 8px 0;
  border-right: 1px solid var(--meis-border-light);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
  min-height: 0;
  max-height: 100%;
  align-self: stretch;
  box-sizing: border-box;
}

.form-tab-nav--side::-webkit-scrollbar {
  width: 4px;
}

.form-tab-nav__item {
  position: relative;
  margin: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.4;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.18s ease, background 0.18s ease;
  text-align: left;
}

.form-tab-nav--top .form-tab-nav__item {
  padding: 10px 2px 12px;
}

.form-tab-nav--side .form-tab-nav__item {
  padding: 8px 10px;
  border-radius: 6px;
  white-space: normal;
  word-break: break-all;
}

.form-tab-nav__item:hover {
  color: #303133;
}

.form-tab-nav--side .form-tab-nav__item:hover {
  background: #f5f7fa;
}

.form-tab-nav__item.is-active {
  color: var(--el-color-primary);
  font-weight: 500;
}

.form-tab-nav--side .form-tab-nav__item.is-active {
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.form-tab-nav--top .form-tab-nav__item.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--el-color-primary);
  border-radius: 2px 2px 0 0;
}

.form-tab-nav--side .form-tab-nav__item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 2px;
  background: var(--el-color-primary);
}
</style>
