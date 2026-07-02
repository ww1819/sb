<template>
  <div class="page-toolbar">
    <div class="toolbar-left">
      <slot name="filters" />
    </div>
    <div class="toolbar-right">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
</style>
