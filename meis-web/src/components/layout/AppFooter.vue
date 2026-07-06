<template>
  <footer class="app-footer">
    <span class="footer-brand">{{ APP_NAME }}</span>
    <span class="footer-sep">·</span>
    <span class="footer-version">v{{ APP_VERSION }}</span>
    <span class="footer-sep">·</span>
    <span class="footer-copy">{{ APP_COPYRIGHT }}</span>
    <span class="footer-sep">·</span>
    <span class="footer-support">{{ APP_SUPPORT }}</span>
  </footer>
</template>

<script setup lang="ts">
import { APP_COPYRIGHT, APP_NAME, APP_SUPPORT, APP_VERSION } from '@/config/app'
</script>

<style scoped>
.app-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 0 4px;
  border-top: 1px solid var(--meis-border-light);
  font-size: 12px;
  color: var(--meis-text-secondary);
  line-height: 1.6;
}

.footer-brand {
  color: var(--meis-text-primary);
  font-weight: 500;
}

.footer-sep {
  opacity: 0.45;
}

.footer-version {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
