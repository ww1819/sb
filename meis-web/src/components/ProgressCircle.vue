<template>
  <div
    class="progress-circle"
    :class="`progress-circle--${variant}`"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <svg
      class="progress-circle__svg"
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      aria-hidden="true"
    >
      <circle
        class="progress-circle__track"
        :cx="center"
        :cy="center"
        :r="normalizedRadius"
        fill="transparent"
        :stroke-width="stroke"
      />
      <circle
        class="progress-circle__bar"
        :cx="center"
        :cy="center"
        :r="normalizedRadius"
        fill="transparent"
        :stroke-width="stroke"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
        stroke-linecap="round"
      />
    </svg>
    <div class="progress-circle__label">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export type ProgressCircleVariant = 'default' | 'neutral' | 'warning' | 'success' | 'error'

const props = withDefaults(
  defineProps<{
    value?: number
    radius?: number
    strokeWidth?: number
    variant?: ProgressCircleVariant
  }>(),
  {
    value: 0,
    radius: 50,
    strokeWidth: 6,
    variant: 'default'
  }
)

const stroke = computed(() => props.strokeWidth)
const normalizedRadius = computed(() => Math.max(props.radius - stroke.value / 2, 1))
const size = computed(() => props.radius * 2)
const center = computed(() => props.radius)
const circumference = computed(() => 2 * Math.PI * normalizedRadius.value)
const clamped = computed(() => Math.min(100, Math.max(0, Number(props.value) || 0)))
const dashOffset = computed(() => circumference.value * (1 - clamped.value / 100))
</script>

<style scoped>
.progress-circle {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.progress-circle__svg {
  display: block;
  transform: rotate(-90deg);
}

.progress-circle__track,
.progress-circle__bar {
  transition: stroke-dashoffset 0.35s ease;
}

.progress-circle__label {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  text-align: center;
  padding: 8px;
  line-height: 1.2;
}

.progress-circle--default {
  --pc-bar: #3b82f6;
  --pc-track: #dbeafe;
}

.progress-circle--neutral {
  --pc-bar: #4b5563;
  --pc-track: #e5e7eb;
}

.progress-circle--warning {
  --pc-bar: #f59e0b;
  --pc-track: #fef3c7;
}

.progress-circle--success {
  --pc-bar: #10b981;
  --pc-track: #d1fae5;
}

.progress-circle--error {
  --pc-bar: #ef4444;
  --pc-track: #fee2e2;
}

.progress-circle__track {
  stroke: var(--pc-track);
}

.progress-circle__bar {
  stroke: var(--pc-bar);
}

:global(html.dark) .progress-circle--default {
  --pc-bar: #60a5fa;
  --pc-track: #1e3a5f;
}

:global(html.dark) .progress-circle--neutral {
  --pc-bar: #9ca3af;
  --pc-track: #374151;
}

:global(html.dark) .progress-circle--warning {
  --pc-bar: #fbbf24;
  --pc-track: #78350f;
}

:global(html.dark) .progress-circle--success {
  --pc-bar: #34d399;
  --pc-track: #064e3b;
}

:global(html.dark) .progress-circle--error {
  --pc-bar: #f87171;
  --pc-track: #7f1d1d;
}
</style>
