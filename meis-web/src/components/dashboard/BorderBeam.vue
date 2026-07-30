<template>
  <div ref="rootRef" class="border-beam">
    <div class="border-beam__content">
      <slot />
    </div>
    <svg
      class="border-beam__svg"
      :viewBox="`0 0 ${box.w || 1} ${box.h || 1}`"
      aria-hidden="true"
    >
      <defs>
        <linearGradient :id="gradId" gradientUnits="userSpaceOnUse" x1="0" y1="0" :x2="box.w || 1" y2="0">
          <stop
            v-for="stop in colorStops"
            :key="`${stop.color}-${stop.percent}`"
            :offset="`${stop.percent}%`"
            :stop-color="stop.color"
          />
        </linearGradient>
      </defs>
      <!-- 淡底边，衬托光束 -->
      <rect
        class="border-beam__track"
        :x="inset"
        :y="inset"
        :width="innerW"
        :height="innerH"
        :rx="rx"
        :ry="rx"
        fill="none"
        :stroke-width="borderWidth"
      />
      <!-- 彗星尾：多层短划叠加，越靠后越细越淡 -->
      <rect
        v-for="layer in cometLayers"
        :key="layer.key"
        class="border-beam__glow"
        :x="inset"
        :y="inset"
        :width="innerW"
        :height="innerH"
        :rx="rx"
        :ry="rx"
        fill="none"
        :stroke="`url(#${gradId})`"
        :stroke-width="borderWidth * layer.widthScale"
        stroke-linecap="round"
        :stroke-opacity="layer.opacity"
        :stroke-dasharray="layer.dashArray"
        :stroke-dashoffset="dashOffset + layer.tailPull"
      />
    </svg>
  </div>
</template>

<script lang="ts">
export type BorderBeamStop = { color: string; percent: number }

/** Ocean 预设（antd BorderBeam 同源色标） */
export const BORDER_BEAM_OCEAN: BorderBeamStop[] = [
  { color: '#1677ff', percent: 0 },
  { color: '#36cfc9', percent: 52 },
  { color: '#95de64', percent: 100 }
]
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    color?: BorderBeamStop[]
    /** 绕边一圈时长（秒） */
    duration?: number
    /** 光束占边框周长的比例（约 12–35） */
    beamPercent?: number
    /** 光束厚度（px） */
    borderWidth?: number
  }>(),
  {
    duration: 2.5,
    beamPercent: 20,
    borderWidth: 2.5,
    color: () => [...BORDER_BEAM_OCEAN]
  }
)

const rootRef = ref<HTMLElement | null>(null)
const box = ref({ w: 0, h: 0 })
const rx = ref(8)
const dashOffset = ref(0)

const inset = computed(() => props.borderWidth / 2)
const innerW = computed(() => Math.max(0, (box.value.w || 0) - inset.value * 2))
const innerH = computed(() => Math.max(0, (box.value.h || 0) - inset.value * 2))
const gradId = `bb-grad-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`

const colorStops = computed(() => {
  const stops = [...props.color].sort((a, b) => a.percent - b.percent)
  return stops.length ? stops : BORDER_BEAM_OCEAN
})

/** 圆角矩形周长（px） */
const perimeter = computed(() => {
  const w = innerW.value
  const h = innerH.value
  const r = Math.min(rx.value, w / 2, h / 2)
  if (w <= 0 || h <= 0) return 0
  return 2 * (w + h - 2 * r) + 2 * Math.PI * r
})

/**
 * 彗星层：从头部到尾流
 * - lenScale：该层划线长度占光束总长
 * - widthScale：线宽相对 borderWidth
 * - opacity：透明度
 * - pullScale：相对光束总长向后拉开的距离（形成渐细尾流）
 */
const COMET_LAYER_DEFS = [
  { key: 'tip', lenScale: 0.18, widthScale: 1, opacity: 1, pullScale: 0 },
  { key: 'body', lenScale: 0.32, widthScale: 0.82, opacity: 0.72, pullScale: 0.1 },
  { key: 'mid', lenScale: 0.48, widthScale: 0.58, opacity: 0.4, pullScale: 0.22 },
  { key: 'tail', lenScale: 0.72, widthScale: 0.38, opacity: 0.2, pullScale: 0.38 },
  { key: 'wake', lenScale: 1, widthScale: 0.22, opacity: 0.08, pullScale: 0.55 }
] as const

const cometLayers = computed(() => {
  const p = perimeter.value
  if (p <= 0) {
    return COMET_LAYER_DEFS.map((d) => ({
      ...d,
      dashArray: '1 1000',
      tailPull: 0
    }))
  }
  const beam = Math.max(36, p * (props.beamPercent / 100))
  return COMET_LAYER_DEFS.map((d) => {
    const len = Math.max(8, beam * d.lenScale)
    const gap = Math.max(1, p - len)
    return {
      key: d.key,
      widthScale: d.widthScale,
      opacity: d.opacity,
      dashArray: `${len.toFixed(2)} ${gap.toFixed(2)}`,
      // 正向 pull：尾流落在运动方向后方（dashOffset 负向前进时）
      tailPull: beam * d.pullScale
    }
  })
})

let ro: ResizeObserver | null = null
let rafId = 0
let lastTs = 0

function measure() {
  const el = rootRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  box.value = {
    w: Math.max(1, Math.round(rect.width * 100) / 100),
    h: Math.max(1, Math.round(rect.height * 100) / 100)
  }
  const cs = getComputedStyle(el)
  const r = Number.parseFloat(cs.borderTopLeftRadius || '8') || 8
  const maxR = Math.min(box.value.w, box.value.h) / 2 - inset.value
  rx.value = Math.max(0, Math.min(r, maxR))
}

function tick(ts: number) {
  if (!lastTs) lastTs = ts
  const dt = Math.min(0.064, (ts - lastTs) / 1000)
  lastTs = ts
  const p = perimeter.value
  if (p > 0 && props.duration > 0) {
    const speed = p / props.duration
    let next = (dashOffset.value - speed * dt) % p
    // JS 负取模可能为负，归一化到 (-p, 0]
    if (next > 0) next -= p
    dashOffset.value = next
  }
  rafId = requestAnimationFrame(tick)
}

function startAnim() {
  stopAnim()
  lastTs = 0
  rafId = requestAnimationFrame(tick)
}

function stopAnim() {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
  lastTs = 0
}

onMounted(async () => {
  await nextTick()
  measure()
  startAnim()
  ro = new ResizeObserver(() => measure())
  if (rootRef.value) ro.observe(rootRef.value)
})

onBeforeUnmount(() => {
  stopAnim()
  ro?.disconnect()
  ro = null
})

watch(
  () => [props.borderWidth, props.duration, props.beamPercent],
  () => {
    measure()
  }
)
</script>

<style scoped>
.border-beam {
  position: relative;
  border-radius: var(--meis-card-radius, 8px);
  isolation: isolate;
}

.border-beam__content {
  position: relative;
  z-index: 1;
  border-radius: inherit;
}

.border-beam__svg {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.border-beam__track {
  stroke: rgba(22, 119, 255, 0.12);
}

.border-beam :deep(.stat-card) {
  border-color: transparent;
}
</style>
