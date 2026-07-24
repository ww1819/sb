<template>
  <AppModal v-model="visible" title="高拍仪拍照" size="lg">
    <div class="eloam">
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
        <el-button :disabled="!cam.cameraOpen.value || cam.busy.value" @click="capture">拍照</el-button>
        <el-button :disabled="!cam.cameraOpen.value" @click="stopPreview">关闭预览</el-button>
        <el-switch
          :model-value="cam.deskewOn.value"
          inline-prompt
          active-text="纠偏"
          inactive-text="纠偏"
          :disabled="!vendor.implemented"
          @change="onDeskew"
        />
      </div>
      <div class="preview-wrap">
        <img v-if="cam.previewDataUrl.value" :src="cam.previewDataUrl.value" class="preview" alt="预览" />
        <div v-else class="preview-empty">
          {{ vendor.implemented ? '打开预览后显示实时画面' : vendor.hint }}
        </div>
      </div>
      <div v-if="shots.length" class="shots">
        <div v-for="(s, i) in shots" :key="s.id" class="shot">
          <img :src="s.dataUrl" alt="" />
          <el-button link type="danger" @click="shots.splice(i, 1)">删除</el-button>
        </div>
      </div>
      <p class="hint">
        {{ vendor.hint }}。无设备时可关闭本窗，改用普通上传。
        HTTPS 站点若无法连接，请检查浏览器是否拦截了 ws://127.0.0.1。
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
import { computed, ref, watch } from 'vue'
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
  }>(),
  { max: 9 }
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
watch(visible, (v) => {
  emit('update:modelValue', v)
  if (!v) teardown()
})

const vendorId = ref<CameraVendorId>(loadPreferredVendorId())
const vendor = computed(() => getCameraVendor(vendorId.value))
const wsUrl = computed(() => vendor.value.wsUrl)
const cam = useEloamCamera(wsUrl)
const shots = ref<{ id: string; base64: string; dataUrl: string }[]>([])
const uploading = ref(false)

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

async function capture() {
  try {
    const b64 = await cam.scanImage()
    if (shots.value.length >= props.max) {
      ElMessage.warning(`最多拍摄 ${props.max} 张`)
      return
    }
    shots.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      base64: b64,
      dataUrl: `data:image/jpeg;base64,${b64}`
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '拍照失败')
  }
}

async function onDeskew(v: string | number | boolean) {
  await cam.setDeskew(!!v)
}

async function stopPreview() {
  await cam.closeCamera()
}

function teardown() {
  cam.disconnect()
  shots.value = []
}

async function confirmUpload() {
  if (!shots.value.length) return
  uploading.value = true
  try {
    const urls: string[] = []
    for (const s of shots.value.slice(0, props.max)) {
      const file = eloamBase64ToFile(s.base64)
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
  width: 96px;
  text-align: center;
}
.shot img {
  width: 96px;
  height: 72px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
}
.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
