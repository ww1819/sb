import { defineStore } from 'pinia'

let timer: ReturnType<typeof setInterval> | null = null

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

export const useRouteProgressStore = defineStore('routeProgress', {
  state: () => ({
    visible: false,
    progress: 0
  }),
  actions: {
    start() {
      clearTimer()
      this.visible = true
      this.progress = 12
      timer = setInterval(() => {
        if (this.progress < 85) {
          const step = this.progress < 40 ? 8 : this.progress < 70 ? 4 : 2
          this.progress = Math.min(this.progress + step, 85)
        }
      }, 180)
    },
    finish() {
      clearTimer()
      this.progress = 100
      window.setTimeout(() => {
        this.visible = false
        this.progress = 0
      }, 260)
    },
    fail() {
      clearTimer()
      this.visible = false
      this.progress = 0
    }
  }
})
