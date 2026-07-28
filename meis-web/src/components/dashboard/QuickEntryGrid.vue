<template>
  <div
    class="quick-entry"
    @mouseenter="hoverPaused = true"
    @mouseleave="hoverPaused = false"
  >
    <div
      ref="viewportRef"
      class="quick-entry-viewport"
      :class="{ 'is-swipeable': pageCount > 1 }"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <div
        class="quick-entry-track"
        :style="trackStyle"
      >
        <div
          v-for="(page, pageIdx) in pages"
          :key="pageIdx"
          class="quick-entry-page"
        >
          <button
            v-for="item in page"
            :key="item.path"
            type="button"
            class="quick-entry-item"
            :style="{
              '--entry-color': item.color,
              '--entry-bg': item.bgColor
            }"
            @click="onItemClick(item.path)"
          >
            <div class="quick-entry-icon">
              <el-icon :size="22"><component :is="item.icon" /></el-icon>
            </div>
            <div class="quick-entry-label">{{ item.label }}</div>
          </button>
        </div>
      </div>
    </div>
    <div v-if="pageCount > 1" class="quick-entry-dots">
      <button
        v-for="i in pageCount"
        :key="i"
        type="button"
        class="quick-entry-dot"
        :class="{ 'is-active': i - 1 === activePage }"
        :aria-label="`第 ${i} 页`"
        @click="goToPage(i - 1)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type Component, type CSSProperties } from 'vue'

export interface QuickEntryItem {
  label: string
  path: string
  icon: Component
  color: string
  bgColor: string
  desc?: string
}

const COLS = 5
const ROWS = 2
const PAGE_SIZE = COLS * ROWS
const ROTATE_MS = 5000
const SWIPE_THRESHOLD = 48

const props = defineProps<{
  items: QuickEntryItem[]
}>()

const emit = defineEmits<{
  navigate: [path: string]
}>()

const viewportRef = ref<HTMLElement | null>(null)
const activePage = ref(0)
const hoverPaused = ref(false)
const dragOffsetPx = ref(0)
const dragging = ref(false)
const suppressClick = ref(false)

let rotateTimer: ReturnType<typeof setInterval> | null = null
let pointerId: number | null = null
let startX = 0

const pages = computed(() => {
  const list = props.items
  if (!list.length) return [[]] as QuickEntryItem[][]
  const out: QuickEntryItem[][] = []
  for (let i = 0; i < list.length; i += PAGE_SIZE) {
    out.push(list.slice(i, i + PAGE_SIZE))
  }
  return out
})

const pageCount = computed(() => pages.value.length)

const trackStyle = computed<CSSProperties>(() => {
  const base = -activePage.value * 100
  const dragPct =
    viewportRef.value && dragging.value
      ? (dragOffsetPx.value / viewportRef.value.clientWidth) * 100
      : 0
  return {
    transform: `translateX(calc(${base}% + ${dragPct}%))`,
    transition: dragging.value ? 'none' : 'transform 0.35s ease'
  }
})

function clampPage(idx: number) {
  const max = Math.max(0, pageCount.value - 1)
  if (idx < 0) return max
  if (idx > max) return 0
  return idx
}

function goToPage(idx: number) {
  activePage.value = clampPage(idx)
}

function stopRotate() {
  if (rotateTimer != null) {
    clearInterval(rotateTimer)
    rotateTimer = null
  }
}

function startRotate() {
  stopRotate()
  if (pageCount.value < 2) return
  rotateTimer = setInterval(() => {
    if (hoverPaused.value || dragging.value) return
    activePage.value = clampPage(activePage.value + 1)
  }, ROTATE_MS)
}

function onItemClick(path: string) {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  emit('navigate', path)
}

function onPointerDown(e: PointerEvent) {
  if (pageCount.value < 2) return
  if (e.button !== 0 && e.pointerType === 'mouse') return
  pointerId = e.pointerId
  startX = e.clientX
  dragOffsetPx.value = 0
  dragging.value = true
  viewportRef.value?.setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value || pointerId !== e.pointerId) return
  dragOffsetPx.value = e.clientX - startX
}

function onPointerUp(e: PointerEvent) {
  if (!dragging.value || (pointerId != null && pointerId !== e.pointerId)) return
  const dx = dragOffsetPx.value
  dragging.value = false
  pointerId = null
  dragOffsetPx.value = 0

  if (Math.abs(dx) >= SWIPE_THRESHOLD) {
    suppressClick.value = true
    goToPage(activePage.value + (dx < 0 ? 1 : -1))
  } else if (Math.abs(dx) > 6) {
    suppressClick.value = true
  }
}

watch(pageCount, (n) => {
  if (activePage.value >= n) activePage.value = Math.max(0, n - 1)
  startRotate()
})

onMounted(() => startRotate())
onUnmounted(() => stopRotate())
</script>

<style scoped>
.quick-entry {
  display: flex;
  flex-direction: column;
  gap: 10px;
  user-select: none;
}

/* padding 留给悬停上浮/阴影，避免被 overflow 裁切光圈 */
.quick-entry-viewport {
  overflow: hidden;
  touch-action: pan-y;
  padding: 6px;
  margin: -6px;
}

.quick-entry-viewport.is-swipeable {
  cursor: grab;
}

.quick-entry-viewport.is-swipeable:active {
  cursor: grabbing;
}

.quick-entry-track {
  display: flex;
  width: 100%;
  will-change: transform;
}

.quick-entry-page {
  flex: 0 0 100%;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(76px, auto));
  gap: var(--meis-space-md);
  min-height: calc(2 * 76px + 1 * var(--meis-space-md));
  box-sizing: border-box;
}

.quick-entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 76px;
  padding: 12px 8px;
  border: 1px solid var(--meis-border-light);
  border-radius: var(--meis-radius-lg);
  background: #fff;
  box-shadow: var(--meis-shadow-sm);
  cursor: pointer;
  text-align: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.quick-entry-item:hover {
  transform: translateY(-2px);
  border-color: var(--entry-color);
  box-shadow: var(--meis-shadow-md);
}

.quick-entry-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: var(--entry-color);
  background: var(--entry-bg);
}

.quick-entry-label {
  font-size: var(--meis-font-caption);
  font-weight: 600;
  color: var(--meis-text-primary);
  line-height: 1.3;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-entry-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.quick-entry-dot {
  width: 7px;
  height: 7px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--meis-border-light, #dcdfe6);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}

.quick-entry-dot.is-active {
  background: var(--el-color-primary);
  transform: scale(1.15);
}
</style>
