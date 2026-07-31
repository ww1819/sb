<template>
  <div class="device-warranty-panel">
    <el-alert
      v-if="!deviceId"
      type="warning"
      :closable="false"
      show-icon
      title="请先保存设备后再维护维保信息"
      class="device-warranty-panel__alert"
    />
    <template v-else>
      <div class="device-warranty-panel__toolbar">
        <el-tag :type="anyInWarranty ? 'success' : 'info'" effect="light">
          {{ anyInWarranty ? '当前在保' : '当前不在保' }}
        </el-tag>
        <el-button v-if="!readonly" type="primary" @click="openCreate">新建维保包</el-button>
        <el-button v-if="!readonly" @click="openJoin">加入已有维保包</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe class="system-table">
        <el-table-column prop="supplier_name" label="维保公司" min-width="140" show-overflow-tooltip />
        <el-table-column prop="start_date" label="开始日期" width="120" />
        <el-table-column prop="end_date" label="结束日期" width="120" />
        <el-table-column prop="under_warranty" label="在保" width="80">
          <template #default="{ row }">{{ row.under_warranty ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="total_amount" label="总价" width="110" align="right" />
        <el-table-column prop="unit_price" label="本机单价" width="110" align="right" />
        <el-table-column prop="coverage_content" label="维保内容" min-width="160" show-overflow-tooltip />
        <el-table-column v-if="!readonly" label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openHeaderLog(row)">变更记录</el-button>
            <el-button link type="danger" @click="onLeave(row)">移出</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无维保信息" :image-size="72" />
        </template>
      </el-table>
    </template>

    <AppModal v-model="formVisible" :title="form.id ? '编辑维保包' : '新建维保包'" size="md">
      <el-form label-width="110px">
        <el-form-item label="维保公司">
          <div class="supplier-row">
            <RefSelect
              v-model="form.supplier_id"
              link-table="supplier"
              placeholder="选择维保公司；清空=院内自主"
            />
            <el-button link type="primary" @click="fillFromMfr">带出设备生产厂家</el-button>
          </div>
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="form.start_date" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="form.end_date" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="总价">
          <el-input-number v-model="form.total_amount" :controls="true" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="本机单价">
          <el-input-number v-model="form.unit_price" :controls="true" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="维保内容">
          <el-input v-model="form.coverage_content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </AppModal>

    <AppModal v-model="joinVisible" title="加入已有维保包" size="lg">
      <el-input
        v-model="joinKw"
        placeholder="搜索维保公司/内容/设备"
        clearable
        class="join-search"
        @keyup.enter="searchJoin"
      />
      <el-table
        v-loading="joinLoading"
        :data="joinRows"
        border
        max-height="360"
        highlight-current-row
        @current-change="(row) => (joinSelected = row)"
      >
        <el-table-column prop="supplier_name" label="维保公司" min-width="120" />
        <el-table-column prop="start_date" label="开始" width="110" />
        <el-table-column prop="end_date" label="结束" width="110" />
        <el-table-column prop="total_amount" label="总价" width="100" align="right" />
        <el-table-column prop="device_count" label="覆盖台数" width="90" />
        <el-table-column prop="coverage_content" label="内容" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-form label-width="90px" class="join-price">
        <el-form-item label="本机单价">
          <el-input-number v-model="joinUnitPrice" :controls="true" :min="0" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!joinSelected" @click="confirmJoin">
          加入
        </el-button>
      </template>
    </AppModal>

    <EntityChangeHistoryDrawer
      v-model="headerLogVisible"
      entity-type="device_warranty"
      :entity-id="headerLogId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import PageEmpty from '@/components/table/PageEmpty.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'

const props = defineProps<{
  deviceId?: string
  deviceCode?: string
  deviceName?: string
  manufacturerName?: string
  readonly?: boolean
}>()

const loading = ref(false)
const saving = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const formVisible = ref(false)
const form = reactive<Record<string, unknown>>({})
const joinVisible = ref(false)
const joinLoading = ref(false)
const joinKw = ref('')
const joinRows = ref<Record<string, unknown>[]>([])
const joinSelected = ref<Record<string, unknown> | null>(null)
const joinUnitPrice = ref<number | undefined>()
const headerLogVisible = ref(false)
const headerLogId = ref('')

const anyInWarranty = computed(() =>
  rows.value.some((r) => r.under_warranty === true || r.under_warranty === 'true')
)

async function load() {
  if (!props.deviceId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const { data } = await http.get(`/asset/warranty/by-device/${props.deviceId}`)
    rows.value = data.data ?? []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  form.supplier_id = null
  form.unit_price = undefined
  formVisible.value = true
}

async function openEdit(row: Record<string, unknown>) {
  Object.keys(form).forEach((k) => delete form[k])
  try {
    const { data } = await http.get(`/asset/warranty/${row.id}`)
    const detail = (data.data ?? {}) as Record<string, unknown>
    Object.assign(form, {
      id: detail.id,
      supplier_id: detail.supplier_id,
      start_date: detail.start_date,
      end_date: detail.end_date,
      total_amount: detail.total_amount != null ? Number(detail.total_amount) : undefined,
      coverage_content: detail.coverage_content,
      remark: detail.remark,
      devices: detail.devices ?? [],
      unit_price: row.unit_price != null ? Number(row.unit_price) : undefined
    })
    formVisible.value = true
  } catch {
    /* 拦截器 */
  }
}

async function save() {
  if (!form.start_date || !form.end_date) {
    ElMessage.warning('请填写维保起止日期')
    return
  }
  saving.value = true
  try {
    let devices = Array.isArray(form.devices)
      ? [...(form.devices as Record<string, unknown>[])]
      : []
    if (!form.id) {
      devices = [
        {
          device_id: props.deviceId,
          device_code: props.deviceCode,
          device_name: props.deviceName,
          unit_price: form.unit_price
        }
      ]
    } else if (props.deviceId) {
      const idx = devices.findIndex((d) => String(d.device_id) === String(props.deviceId))
      if (idx >= 0) {
        devices[idx] = { ...devices[idx], unit_price: form.unit_price }
      } else {
        devices.push({
          device_id: props.deviceId,
          device_code: props.deviceCode,
          device_name: props.deviceName,
          unit_price: form.unit_price
        })
      }
    }
    await http.post('/asset/warranty', {
      id: form.id,
      supplier_id: form.supplier_id || null,
      start_date: form.start_date,
      end_date: form.end_date,
      total_amount: form.total_amount,
      coverage_content: form.coverage_content,
      remark: form.remark,
      devices
    })
    ElMessage.success('保存成功')
    formVisible.value = false
    await load()
  } catch {
    // 拦截器
  } finally {
    saving.value = false
  }
}

async function onLeave(row: Record<string, unknown>) {
  const linkId = row.link_id
  if (!linkId) {
    ElMessage.warning('缺少关联记录')
    return
  }
  try {
    await ElMessageBox.confirm('确定将本机移出该维保包？', '移出确认', { type: 'warning' })
  } catch {
    return
  }
  await http.delete(`/asset/warranty/${row.id}/devices/${linkId}`)
  ElMessage.success('已移出')
  await load()
}

function openJoin() {
  joinKw.value = ''
  joinSelected.value = null
  joinUnitPrice.value = undefined
  joinVisible.value = true
  void searchJoin()
}

async function searchJoin() {
  joinLoading.value = true
  try {
    const { data } = await http.get('/asset/warranty/page', {
      params: { page: 1, size: 50, keyword: joinKw.value || undefined }
    })
    joinRows.value = data.data?.records ?? []
  } finally {
    joinLoading.value = false
  }
}

async function confirmJoin() {
  if (!joinSelected.value?.id || !props.deviceId) return
  saving.value = true
  try {
    await http.post(`/asset/warranty/${joinSelected.value.id}/devices`, {
      device_id: props.deviceId,
      unit_price: joinUnitPrice.value
    })
    ElMessage.success('已加入维保包')
    joinVisible.value = false
    await load()
  } catch {
    /* 拦截器 */
  } finally {
    saving.value = false
  }
}

function openHeaderLog(row: Record<string, unknown>) {
  headerLogId.value = String(row.id ?? '')
  headerLogVisible.value = true
}

async function fillFromMfr() {
  let name = String(props.manufacturerName ?? '').trim()
  if (!name && props.deviceId) {
    try {
      const { data } = await http.get('/asset/device/page', {
        params: { page: 1, size: 1, device_code: props.deviceCode || undefined }
      })
      const row = (data.data?.records ?? [])[0] as Record<string, unknown> | undefined
      name = String(row?.manufacturer_name ?? '').trim()
    } catch {
      /* ignore */
    }
  }
  if (!name) {
    ElMessage.warning('该设备未维护生产厂家名称')
    return
  }
  const { data } = await http.get('/system/supplier/page', {
    params: { page: 1, size: 10, keyword: name }
  })
  const list = (data.data?.records ?? []) as Record<string, unknown>[]
  if (!list.length) {
    ElMessage.warning(`未在供应商中找到「${name}」，请先建档`)
    return
  }
  form.supplier_id = String(list[0].id)
  ElMessage.success(`已带出：${list[0].supplier_name ?? name}`)
}

watch(
  () => props.deviceId,
  () => void load(),
  { immediate: true }
)
</script>

<style scoped>
.device-warranty-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 240px;
}
.device-warranty-panel__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.supplier-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.supplier-row :deep(.el-select) {
  flex: 1;
}
.join-search {
  margin-bottom: 8px;
}
.join-price {
  margin-top: 12px;
}
</style>
