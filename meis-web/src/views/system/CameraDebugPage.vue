<template>
  <div class="camera-debug">
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>高拍仪调试</span>
          <el-tag size="small" type="info">系统管理 · 外设</el-tag>
        </div>
      </template>
      <el-form label-width="100px" size="small" class="form">
        <el-form-item label="厂家品牌">
          <el-select v-model="vendorId" style="width: 280px" @change="onVendorChange">
            <el-option
              v-for="v in CAMERA_VENDORS"
              :key="v.id"
              :label="v.label + (v.implemented ? '' : '（待接入）')"
              :value="v.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="服务地址">
          <el-input :model-value="vendor.wsUrl || '—'" readonly style="max-width: 360px" />
        </el-form-item>
        <el-form-item label="说明">
          <span class="hint">{{ vendor.hint }}</span>
        </el-form-item>
        <el-form-item label="连接状态">
          <el-tag :type="cam.connected.value ? 'success' : 'info'">
            {{ cam.connected.value ? '已连接' : '未连接' }}
          </el-tag>
          <el-tag v-if="cam.cameraOpen.value" type="success" class="ml">预览中</el-tag>
          <el-tag v-if="cam.deviceCount.value" class="ml">设备数 {{ cam.deviceCount.value }}</el-tag>
        </el-form-item>
        <el-form-item label="操作">
          <el-button type="primary" :loading="cam.busy.value" :disabled="!vendor.implemented" @click="start">
            打开预览
          </el-button>
          <el-button :disabled="!cam.cameraOpen.value || cam.busy.value" @click="capture">拍照测试</el-button>
          <el-button :disabled="!cam.cameraOpen.value" @click="stopPreview">关闭预览</el-button>
          <el-button @click="disconnect">断开</el-button>
          <el-switch
            class="ml"
            :model-value="cam.deskewOn.value"
            inline-prompt
            active-text="纠偏开"
            inactive-text="纠偏关"
            :disabled="!vendor.implemented"
            @change="onDeskew"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="cam.error.value"
        :title="cam.error.value"
        type="warning"
        show-icon
        :closable="false"
        class="mb"
      />
      <div class="preview-wrap">
        <img v-if="cam.previewDataUrl.value" :src="cam.previewDataUrl.value" class="preview" alt="预览" />
        <div v-else class="preview-empty">连接并打开预览后显示画面</div>
      </div>
      <div v-if="lastShot" class="last-shot">
        <div class="sub">最近拍照</div>
        <img :src="lastShot" alt="shot" />
      </div>
      <div class="log-box">
        <div class="sub">调试日志</div>
        <pre>{{ logs.join('\n') || '暂无日志' }}</pre>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CAMERA_VENDORS,
  getCameraVendor,
  loadPreferredVendorId,
  savePreferredVendorId,
  type CameraVendorId
} from '@/config/cameraVendors'
import { useEloamCamera } from '@/composables/useEloamCamera'

const vendorId = ref<CameraVendorId>(loadPreferredVendorId())
const vendor = computed(() => getCameraVendor(vendorId.value))
const wsUrl = computed(() => vendor.value.wsUrl)
const cam = useEloamCamera(wsUrl)
const lastShot = ref('')
const logs = ref<string[]>([])

function log(msg: string) {
  const t = new Date().toLocaleTimeString()
  logs.value = [`[${t}] ${msg}`, ...logs.value].slice(0, 80)
}

function onVendorChange(id: CameraVendorId) {
  savePreferredVendorId(id)
  cam.disconnect()
  lastShot.value = ''
  log(`切换厂家：${getCameraVendor(id).label}`)
}

async function start() {
  if (!vendor.value.implemented) {
    ElMessage.warning('该厂家适配尚未接入')
    log('厂家未实现，跳过打开')
    return
  }
  try {
    log(`连接 ${vendor.value.wsUrl} …`)
    await cam.initAndOpen()
    log('预览已打开')
    ElMessage.success('预览已打开')
  } catch (e) {
    const m = e instanceof Error ? e.message : '打开失败'
    log(m)
    ElMessage.warning(m)
  }
}

async function capture() {
  try {
    const b64 = await cam.scanImage()
    lastShot.value = `data:image/jpeg;base64,${b64}`
    log(`拍照成功，base64 长度 ${b64.length}`)
    ElMessage.success('拍照成功')
  } catch (e) {
    const m = e instanceof Error ? e.message : '拍照失败'
    log(m)
    ElMessage.error(m)
  }
}

async function onDeskew(v: string | number | boolean) {
  await cam.setDeskew(!!v)
  log(`纠偏：${v ? '开' : '关'}`)
}

async function stopPreview() {
  await cam.closeCamera()
  log('已关闭预览')
}

function disconnect() {
  cam.disconnect()
  log('已断开连接')
}

onBeforeUnmount(() => cam.disconnect())
</script>

<style scoped>
.camera-debug {
  padding: 12px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.form {
  max-width: 920px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.ml {
  margin-left: 8px;
}
.mb {
  margin: 8px 0 12px;
}
.preview-wrap {
  width: 100%;
  min-height: 320px;
  background: #111;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.preview {
  max-width: 100%;
  max-height: 480px;
  object-fit: contain;
}
.preview-empty {
  color: #98a2b3;
  font-size: 13px;
}
.last-shot {
  margin-top: 16px;
}
.last-shot img {
  max-width: 240px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color);
}
.sub {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.log-box {
  margin-top: 16px;
}
.log-box pre {
  margin: 0;
  padding: 12px;
  max-height: 220px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
