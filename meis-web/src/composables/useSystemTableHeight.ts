import { computed, inject, type Ref } from 'vue'

export function useSystemTableHeight(fallback = 400) {
  const height = inject<Ref<number> | null>('systemTableHeight', null)
  return computed(() => height?.value ?? fallback)
}
