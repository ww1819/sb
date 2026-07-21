<template>
  <view class="page">
    <view class="tip">请在下方空白处手写签名</view>
    <canvas
      canvas-id="sigCanvas"
      class="canvas"
      :style="{ width: canvasW + 'px', height: canvasH + 'px' }"
      @touchstart="onStart"
      @touchmove="onMove"
      @touchend="onEnd"
      disable-scroll
    />
    <view class="actions">
      <button size="mini" @click="clear">清除</button>
      <button type="primary" size="mini" :loading="saving" @click="confirm">确认上传</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { getCurrentInstance, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { uploadFile } from '@/api/upload'

const canvasW = ref(320)
const canvasH = ref(200)
const saving = ref(false)
let ctx: UniApp.CanvasContext | null = null
let drawing = false
let lastX = 0
let lastY = 0
let hasStroke = false
let eventChannel: { emit: (name: string, data?: unknown) => void } | null = null

onLoad(() => {
  const inst = getCurrentInstance()?.proxy as { getOpenerEventChannel?: () => typeof eventChannel }
  eventChannel = inst?.getOpenerEventChannel?.() ?? null
})

onMounted(() => {
  try {
    const sys = uni.getSystemInfoSync()
    canvasW.value = Math.min(sys.windowWidth - 32, 360)
    canvasH.value = Math.round(canvasW.value * 0.55)
  } catch {
    /* keep defaults */
  }
  ctx = uni.createCanvasContext('sigCanvas')
  if (ctx) {
    ctx.setStrokeStyle('#1f2a37')
    ctx.setLineWidth(3)
    ctx.setLineCap('round')
    ctx.setLineJoin('round')
    ctx.setFillStyle('#ffffff')
    ctx.fillRect(0, 0, canvasW.value, canvasH.value)
    ctx.draw()
  }
})

function pos(e: { touches?: { x: number; y: number }[] }) {
  const t = e.touches?.[0]
  return { x: t?.x ?? 0, y: t?.y ?? 0 }
}

function onStart(e: { touches?: { x: number; y: number }[] }) {
  drawing = true
  const p = pos(e)
  lastX = p.x
  lastY = p.y
}

function onMove(e: { touches?: { x: number; y: number }[] }) {
  if (!drawing || !ctx) return
  const p = pos(e)
  ctx.beginPath()
  ctx.moveTo(lastX, lastY)
  ctx.lineTo(p.x, p.y)
  ctx.stroke()
  ctx.draw(true)
  lastX = p.x
  lastY = p.y
  hasStroke = true
}

function onEnd() {
  drawing = false
}

function clear() {
  hasStroke = false
  if (!ctx) return
  ctx.setFillStyle('#ffffff')
  ctx.fillRect(0, 0, canvasW.value, canvasH.value)
  ctx.draw()
}

function confirm() {
  if (!hasStroke) {
    uni.showToast({ title: '请先签名', icon: 'none' })
    return
  }
  saving.value = true
  uni.canvasToTempFilePath({
    canvasId: 'sigCanvas',
    success: async (res) => {
      try {
        const url = await uploadFile(res.tempFilePath, 'signature.png')
        eventChannel?.emit('signed', url)
        uni.navigateBack()
      } catch (e: unknown) {
        uni.showToast({ title: e instanceof Error ? e.message : '上传失败', icon: 'none' })
      } finally {
        saving.value = false
      }
    },
    fail: () => {
      saving.value = false
      uni.showToast({ title: '导出签名失败', icon: 'none' })
    }
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f5f7fa;
}
.tip {
  font-size: 26rpx;
  color: #667085;
  margin-bottom: 16rpx;
}
.canvas {
  background: #fff;
  border: 1px solid #d0d5dd;
  border-radius: 12rpx;
}
.actions {
  margin-top: 28rpx;
  display: flex;
  gap: 16rpx;
}
.actions button {
  flex: 1;
  margin: 0;
}
</style>
