<template>
  <div class="device-power-monitor">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再绑定电流监测标签"
      class="device-power-monitor__alert"
    />

    <template v-else>
      <section class="device-power-monitor__section">
        <div class="device-power-monitor__section-head">
          <h4>电流监测标签</h4>
          <el-button link type="primary" @click="goTagMenu">去电流标签菜单新建</el-button>
        </div>

        <div v-loading="loadingTag" class="device-power-monitor__bind">
          <template v-if="boundTag">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="标签编码">{{ boundTag.tag_code }}</el-descriptions-item>
              <el-descriptions-item label="标签名称">{{ boundTag.tag_name }}</el-descriptions-item>
            </el-descriptions>
            <div class="device-power-monitor__actions">
              <el-button type="danger" plain :loading="binding" :disabled="readonly" @click="onUnbind">
                解绑标签
              </el-button>
            </div>
          </template>
          <template v-else>
            <p class="device-power-monitor__hint">当前未绑定标签。请选择已有且未占用设备的标签进行绑定。</p>
            <div class="device-power-monitor__pick">
              <el-select
                v-model="pickTagId"
                filterable
                remote
                clearable
                reserve-keyword
                placeholder="输入标签编码搜索未绑定标签"
                :remote-method="searchUnboundTags"
                :loading="searching"
                :disabled="readonly"
                class="device-power-monitor__select"
              >
                <el-option
                  v-for="o in tagOptions"
                  :key="o.id"
                  :label="`${o.tag_code}（${o.tag_name}）`"
                  :value="o.id"
                />
              </el-select>
              <el-button type="primary" :loading="binding" :disabled="readonly || !pickTagId" @click="onBind">
                绑定
              </el-button>
            </div>
          </template>
        </div>
      </section>

      <section class="device-power-monitor__section">
        <div class="device-power-monitor__section-head">
          <h4>待机电流上下限</h4>
        </div>
        <el-form label-width="160px" class="device-power-monitor__limits" inline>
          <el-form-item label="待机电流上限(mA)">
            <el-input-number
              v-model="maxMa"
              :disabled="readonly"
              :controls="true"
              class="device-power-monitor__num"
            />
          </el-form-item>
          <el-form-item label="待机电流下限(mA)">
            <el-input-number
              v-model="minMa"
              :disabled="readonly"
              :controls="true"
              class="device-power-monitor__num"
            />
          </el-form-item>
          <el-form-item v-if="boundTag && !readonly">
            <el-button type="primary" plain :loading="savingLimits" @click="onSaveLimits">保存上下限</el-button>
          </el-form-item>
        </el-form>
        <p v-if="!boundTag" class="device-power-monitor__hint">
          未绑定标签时，上下限随抽屉「保存」写入设备台账。
        </p>
      </section>

      <section class="device-power-monitor__section">
        <div class="device-power-monitor__section-head">
          <h4>电流监测记录</h4>
        </div>
        <DeviceCurrentReadingPanel :device-id="deviceId" />
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import DeviceCurrentReadingPanel from '@/components/asset/tabs/DeviceCurrentReadingPanel.vue'

const props = defineProps<{
  deviceId?: string
  deviceCode?: string
  deviceName?: string
  model: Record<string, unknown>
  readonly?: boolean
}>()

const router = useRouter()
const loadingTag = ref(false)
const searching = ref(false)
const binding = ref(false)
const savingLimits = ref(false)
const boundTag = ref<Record<string, unknown> | null>(null)
const pickTagId = ref<string>()
const tagOptions = ref<{ id: string; tag_code: string; tag_name: string }[]>([])

const maxMa = computed({
  get: () =>
    props.model.standby_current_max_ma != null && props.model.standby_current_max_ma !== ''
      ? Number(props.model.standby_current_max_ma)
      : undefined,
  set: (v: number | undefined) => {
    props.model.standby_current_max_ma = v ?? null
  }
})

const minMa = computed({
  get: () =>
    props.model.standby_current_min_ma != null && props.model.standby_current_min_ma !== ''
      ? Number(props.model.standby_current_min_ma)
      : undefined,
  set: (v: number | undefined) => {
    props.model.standby_current_min_ma = v ?? null
  }
})

async function loadBoundTag() {
  if (!props.deviceId) {
    boundTag.value = null
    return
  }
  loadingTag.value = true
  try {
    const { data } = await http.get(`/power/device/${props.deviceId}/tag`)
    boundTag.value = data.data ?? null
    if (boundTag.value) {
      if (boundTag.value.standby_current_max_ma != null) {
        props.model.standby_current_max_ma = boundTag.value.standby_current_max_ma
      }
      if (boundTag.value.standby_current_min_ma != null) {
        props.model.standby_current_min_ma = boundTag.value.standby_current_min_ma
      }
    }
  } finally {
    loadingTag.value = false
  }
}

async function searchUnboundTags(kw: string) {
  searching.value = true
  try {
    const { data } = await http.get('/power/tag/page', {
      params: {
        page: 1,
        size: 20,
        unboundOnly: true,
        keyword: kw?.trim() || undefined
      }
    })
    const rows = (data.data?.records ?? []) as Record<string, unknown>[]
    tagOptions.value = rows.map((r) => ({
      id: String(r.id),
      tag_code: String(r.tag_code ?? ''),
      tag_name: String(r.tag_name ?? '')
    }))
  } finally {
    searching.value = false
  }
}

async function onBind() {
  if (!props.deviceId || !pickTagId.value) return
  binding.value = true
  try {
    const { data: detail } = await http.get(`/power/tag/${pickTagId.value}`)
    const tag = detail.data as Record<string, unknown>
    if (!tag?.id) {
      ElMessage.error('标签不存在')
      return
    }
    if (tag.device_id) {
      ElMessage.warning('该标签已绑定其他设备，请另选未绑定标签')
      return
    }
    await http.post('/power/tag', {
      id: tag.id,
      tag_code: tag.tag_code,
      tag_name: tag.tag_name,
      station_id: tag.station_id ?? null,
      rated_power: tag.rated_power,
      install_date: tag.install_date,
      is_active: tag.is_active ?? true,
      remark: tag.remark,
      device_id: props.deviceId,
      device_code: props.deviceCode || null,
      device_name: props.deviceName || null,
      client: 'web'
    })
    ElMessage.success('绑定成功')
    pickTagId.value = undefined
    tagOptions.value = []
    await loadBoundTag()
  } catch {
    // 业务错误由拦截器提示（含一设备一标签）
  } finally {
    binding.value = false
  }
}

async function onUnbind() {
  if (!boundTag.value?.id) return
  try {
    await ElMessageBox.confirm('确定解绑当前电流监测标签？', '解绑确认', { type: 'warning' })
  } catch {
    return
  }
  binding.value = true
  try {
    const tag = boundTag.value
    await http.post('/power/tag', {
      id: tag.id,
      tag_code: tag.tag_code,
      tag_name: tag.tag_name,
      station_id: tag.station_id ?? null,
      rated_power: tag.rated_power,
      install_date: tag.install_date,
      is_active: tag.is_active ?? true,
      remark: tag.remark,
      device_id: null,
      client: 'web'
    })
    ElMessage.success('已解绑')
    await loadBoundTag()
  } catch {
    // 拦截器
  } finally {
    binding.value = false
  }
}

async function onSaveLimits() {
  if (!boundTag.value?.id) return
  if (maxMa.value == null || minMa.value == null) {
    ElMessage.warning('请填写待机电流上下限')
    return
  }
  savingLimits.value = true
  try {
    await http.put(`/power/tag/${boundTag.value.id}/standby-limits`, {
      standby_current_max_ma: maxMa.value,
      standby_current_min_ma: minMa.value
    })
    ElMessage.success('已回写设备台账')
  } catch {
    // 拦截器
  } finally {
    savingLimits.value = false
  }
}

function goTagMenu() {
  void router.push('/power/tag')
}

watch(
  () => props.deviceId,
  () => {
    void loadBoundTag()
    void searchUnboundTags('')
  },
  { immediate: true }
)
</script>

<style scoped>
.device-power-monitor {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 280px;
}

.device-power-monitor__alert {
  margin-bottom: 4px;
}

.device-power-monitor__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.device-power-monitor__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.device-power-monitor__section-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.device-power-monitor__hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.device-power-monitor__pick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.device-power-monitor__select {
  width: 320px;
  max-width: 100%;
}

.device-power-monitor__actions {
  margin-top: 10px;
}

.device-power-monitor__num {
  width: 180px;
}
</style>
