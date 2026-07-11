<template>
  <div class="leased-device-page">
    <CrudPage ref="crudRef" :config="pageConfig">
      <template #toolbar-extra>
        <el-checkbox v-model="expiringOnly" @change="reload">仅显示30天内到期</el-checkbox>
      </template>
      <template #row-actions="{ row }">
        <el-button v-if="row.status === 'active'" link type="primary" @click="openRenew(row)">续租</el-button>
        <el-button v-if="row.status === 'active'" link type="warning" @click="returnLease(row)">退租</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="renewVisible" title="租赁续租" size="sm">
      <el-form label-width="100px">
        <el-form-item label="到期日">
          <el-date-picker v-model="renewForm.lease_end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="月租金">
          <el-input-number v-model="renewForm.monthly_rent" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRenew">确认续租</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import { getPageConfig } from '@/config/pageRegistry'

const baseConfig = getPageConfig('/special/leased')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const expiringOnly = ref(false)
const renewVisible = ref(false)
const renewTargetId = ref<string>()
const renewForm = ref<{ lease_end_date?: string; monthly_rent?: number }>({})

const pageConfig = computed(() => ({
  ...baseConfig,
  listParams: { expiringOnly: expiringOnly.value || undefined }
}))

function reload() {
  crudRef.value?.load()
}

function openRenew(row: Record<string, unknown>) {
  renewTargetId.value = String(row.id)
  renewForm.value = {
    lease_end_date: row.lease_end_date as string | undefined,
    monthly_rent: row.monthly_rent as number | undefined
  }
  renewVisible.value = true
}

async function submitRenew() {
  if (!renewTargetId.value) return
  await http.post(`/special/leased/${renewTargetId.value}/renew`, renewForm.value)
  ElMessage.success('续租成功')
  renewVisible.value = false
  reload()
}

async function returnLease(row: Record<string, unknown>) {
  await ElMessageBox.confirm('确认办理退租？', '退租', { type: 'warning' })
  await http.post(`/special/leased/${row.id}/return`)
  ElMessage.success('已退租')
  reload()
}
</script>
