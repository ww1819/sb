<template>
  <AppModal v-model="visible" title="高拍仪拍照" size="lg">
    <div class="eloam" tabindex="0" @keydown="onKeydown">
      <el-alert
        v-if="cam.error.value"
        :title="cam.error.value"
        type="warning"
        show-icon
        :closable="false"
        class="mb"
      />
      <div class="toolbar">
        <span class="label">厂家</span>
        <el-select v-model="vendorId" style="width: 200px" :disabled="cam.cameraOpen.value" @change="onVendorChange">
          <el-option
            v-for="v in CAMERA_VENDORS"
            :key="v.id"
            :label="v.label"
            :value="v.id"
            :disabled="!v.implemented"
          />
        </el-select>
        <el-button
          type="primary"
          :loading="cam.busy.value"
          :disabled="cam.cameraOpen.value || !vendor.implemented"
          @click="start"
        >
          {{ cam.cameraOpen.value ? '预览中' : '打开预览' }}
        </el-button>
        <el-button :disabled="!cam.cameraOpen.value || cam.busy.value" @click="capture">拍照（空格）</el-button>
        <el-button :disabled="!cam.cameraOpen.value" @click="stopPreview">关闭预览</el-button>
        <el-switch
          :model-value="cam.deskewOn.value"
          inline-prompt
          active-text="纠偏"
          inactive-text="纠偏"
          :disabled="!vendor.implemented"
          @change="onDeskew"
        />
        <el-switch
          :model-value="cam.flashOn.value"
          inline-prompt
          active-text="补光"
          inactive-text="补光"
          :disabled="!vendor.implemented || !cam.connected.value"
          @change="onFlash"
        />
        <span class="label">成片方向</span>
        <el-radio-group v-model="captureAngle" size="small">
          <el-radio-button :value="0">横向</el-radio-button>
          <el-radio-button :value="90">纵向</el-radio-button>
        </el-radio-group>
      </div>
      <div class="preview-wrap">
        <img
          v-if="cam.previewDataUrl.value"
          :src="cam.previewDataUrl.value"
          class="preview"
          :style="{ transform: `rotate(${captureAngle}deg)` }"
          alt="预览"
        />
        <div v-else class="preview-empty">
          {{ vendor.implemented ? '打开预览后显示实时画面（进窗将自动尝试开预览）' : vendor.hint }}
        </div>
      </div>
      <div v-if="shots.length" class="shots">
        <div v-for="(s, i) in shots" :key="s.id" class="shot">
          <img :src="s.dataUrl" alt="" />
          <el-input v-model="s.name" size="small" class="shot-name" placeholder="文件名" />
          <div class="shot-actions">
            <el-button link type="primary" @click="rotateShot(i, 270)">↺</el-button>
            <el-button link type="primary" @click="rotateShot(i, 90)">↻</el-button>
            <el-button link type="danger" @click="shots.splice(i, 1)">删除</el-button>
          </div>
        </div>
      </div>
      <p class="hint">
        {{ vendor.hint }}。空格连拍；补光灯视驱动支持情况。横/纵向对成片 canvas 旋转后上传。
      </p>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!shots.length" @click="confirmUpload">
        采用并上传（{{ shots.length }}）
      </el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import {
  CAMERA_VENDORS,
  getCameraVendor,
  loadPreferredVendorId,
  savePreferredVendorId,
  type CameraVendorId
} from '@/config/cameraVendors'
import { eloamBase64ToFile, useEloamCamera } from '@/composables/useEloamCamera'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    max?: number
    /** 档案场景默认进窗自动开预览 */
    autoOpen?: boolean
  }>(),
  { max: 9, autoOpen: true }
)

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  done: [urls: string[]]
}>()

const visible = ref(props.modelValue)
watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
  }
)
watch(visible, async (v) => {
  emit('update:modelValue', v)
  if (!v) {
    teardown()
    return
  }
  await nextTick()
  if (props.autoOpen && vendor.value.implemented && !cam.cameraOpen.value) {
    try {
      await cam.initAndOpen()
    } catch {
      /* 无设备时保留手动打开 */
    }
  }
})

const vendorId = ref<CameraVendorId>(loadPreferredVendorId())
const vendor = computed(() => getCameraVendor(vendorId.value))
const wsUrl = computed(() => vendor.value.wsUrl)
const cam = useEloamCamera(wsUrl)
const shots = ref<{ id: string; base64: string; dataUrl: string; name: string }[]>([])
const uploading = ref(false)
const captureAngle = ref<0 | 90>(0)

function onVendorChange(id: CameraVendorId) {
  savePreferredVendorId(id)
  cam.disconnect()
  if (!getCameraVendor(id).implemented) {
    ElMessage.info('该厂家适配尚未接入，请选择已支持的厂家')
  }
}

async function start() {
  if (!vendor.value.implemented) {
    ElMessage.warning('当前厂家未接入')
    return
  }
  try {
    await cam.initAndOpen()
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '打开高拍仪失败')
  }
}

function rotateBase64Image(base64: string, angle: number): Promise<string> {
  const pure = base64.includes(',') ? base64.split(',')[1]! : base64
  if (!angle || angle % 360 === 0) return Promise.resolve(pure)
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      try {
        const rad = ((angle % 360) * Math.PI) / 180
        const swap = angle % 180 !== 0
        const canvas = document.createElement('canvas')
        canvas.width = swap ? img.height : img.width
        canvas.height = swap ? img.width : img.height
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          reject(new Error('无法创建画布'))
          return
        }
        ctx.translate(canvas.width / 2, canvas.height / 2)
        ctx.rotate(rad)
        ctx.drawImage(img, -img.width / 2, -img.height / 2)
        const dataUrl = canvas.toDataURL('image/jpeg', 0.92)
        resolve(dataUrl.split(',')[1] || pure)
      } catch (e) {
        reject(e instanceof Error ? e : new Error('旋转失败'))
      }
    }
    img.onerror = () => reject(new Error('图片加载失败'))
    img.src = `data:image/jpeg;base64,${pure}`
  })
}

async function capture() {
  try {
    const raw = await cam.scanImage()
    if (shots.value.length >= props.max) {
      ElMessage.warning(`最多拍摄 ${props.max} 张`)
      return
    }
    const b64 = await rotateBase64Image(raw, captureAngle.value)
    const n = shots.value.length + 1
    shots.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      base64: b64,
      dataUrl: `data:image/jpeg;base64,${b64}`,
      name: `高拍仪_${Date.now()}_${n}.jpg`
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '拍照失败')
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.code !== 'Space' && e.key !== ' ') return
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  e.preventDefault()
  if (cam.cameraOpen.value && !cam.busy.value) void capture()
}

async function rotateShot(index: number, angle: number) {
  const item = shots.value[index]
  if (!item) return
  try {
    const b64 = await rotateBase64Image(item.base64, angle)
    item.base64 = b64
    item.dataUrl = `data:image/jpeg;base64,${b64}`
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '旋转失败')
  }
}

async function onDeskew(v: string | number | boolean) {
  await cam.setDeskew(!!v)
}

async function onFlash(v: string | number | boolean) {
  await cam.setFlashLight(!!v)
}

async function stopPreview() {
  await cam.closeCamera()
}

function teardown() {
  void cam.setFlashLight(false, true)
  cam.disconnect()
  shots.value = []
}

async function confirmUpload() {
  if (!shots.value.length) return
  uploading.value = true
  try {
    const urls: string[] = []
    for (const s of shots.value.slice(0, props.max)) {
      const filename = (s.name || `eloam-${Date.now()}.jpg`).replace(/[\\/:*?"<>|]/g, '_')
      const file = eloamBase64ToFile(s.base64, filename.endsWith('.jpg') ? filename : `${filename}.jpg`)
      const form = new FormData()
      form.append('file', file)
      const { data } = await http.post('/file/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      if (data.code === 0 && data.data?.url) {
        urls.push(String(data.data.url))
      } else {
        throw new Error(data.message || '上传失败')
      }
    }
    emit('done', urls)
    ElMessage.success(`已上传 ${urls.length} 张`)
    visible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.eloam {
  outline: none;
}
.eloam .mb {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.preview-wrap {
  width: 100%;
  min-height: 280px;
  background: #111;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.preview {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
  transition: transform 0.2s ease;
}
.preview-empty {
  color: #98a2b3;
  font-size: 13px;
  padding: 48px;
  text-align: center;
}
.shots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.shot {
  width: 120px;
  text-align: center;
}
.shot img {
  width: 120px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
}
.shot-name {
  margin-top: 4px;
}
.shot-actions {
  display: flex;
  justify-content: center;
  gap: 2px;
}
.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
