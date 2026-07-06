import { nextTick, onActivated, onMounted, onUnmounted, ref, type Ref } from 'vue'

export function useTableHeight(containerRef: Ref<HTMLElement | null | undefined>, minHeight = 200) {
  const height = ref(minHeight)
  let observer: ResizeObserver | null = null

  function measure() {
    const el = containerRef.value
    if (!el) return
    const h = Math.floor(el.clientHeight)
    if (h > 0) {
      height.value = Math.max(minHeight, h)
    }
  }

  function setupObserver() {
    const el = containerRef.value
    if (!el || typeof ResizeObserver === 'undefined') return
    observer?.disconnect()
    observer = new ResizeObserver(measure)
    observer.observe(el)
  }

  function scheduleMeasure() {
    nextTick(() => {
      measure()
      requestAnimationFrame(measure)
    })
  }

  onMounted(() => {
    scheduleMeasure()
    setupObserver()
    // Layout may settle after tab transition; measure again shortly.
    setTimeout(scheduleMeasure, 50)
    setTimeout(scheduleMeasure, 200)
  })

  onActivated(scheduleMeasure)

  onUnmounted(() => {
    observer?.disconnect()
    observer = null
  })

  return height
}
