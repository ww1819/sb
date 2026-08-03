<template>
  <WorkflowCrudPage
    :config="config"
    save-url="/asset/scrap"
    business-type="device_scrap"
    hide-add
    force-view-mode
    :can-edit="() => false"
    :can-delete="() => false"
  >
    <template #toolbar-extra="{ form, reload }">
      <el-button v-if="form?.id && form.status === 'approved'" type="warning" @click="openDispose(form, reload)">
        处置归档
      </el-button>
    </template>
  </WorkflowCrudPage>

  <AppModal v-model="disposeVisible" title="报废处置归档" size="md">
    <el-form label-width="100px" size="small">
      <el-form-item label="处置方式" required>
        <el-select v-model="disposeForm.disposal_method" placeholder="请选择" style="width: 100%">
          <el-option v-for="o in disposalOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="处置去向">
        <el-input v-model="disposeForm.disposal_destination" placeholder="单位/渠道说明" />
      </el-form-item>
      <el-form-item label="证明附件URL">
        <el-input v-model="disposeForm.disposal_proof_url" placeholder="可选" />
      </el-form-item>
      <el-form-item label="处置日期">
        <el-date-picker
          v-model="disposeForm.disposal_date"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="disposeVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmDispose">确认归档</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { useDict } from '@/composables/useDict'

const config = computed(() => getPageConfig('/warehouse/scrap-query')!)
const { loadDict } = useDict()
const disposalOptions = ref<{ value: string; label: string }[]>([])
const disposeVisible = ref(false)
const disposeId = ref('')
const reloadFn = ref<(() => void) | undefined>()
const disposeForm = reactive({
  disposal_method: '',
  disposal_destination: '',
  disposal_proof_url: '',
  disposal_date: '' as string
})

function openDispose(form: Record<string, unknown>, reload?: () => void) {
  disposeId.value = String(form.id)
  disposeForm.disposal_method = String(form.disposal_method ?? '')
  disposeForm.disposal_destination = String(form.disposal_destination ?? '')
  disposeForm.disposal_proof_url = String(form.disposal_proof_url ?? '')
  disposeForm.disposal_date = String(form.disposal_date ?? '').slice(0, 10)
  reloadFn.value = reload
  disposeVisible.value = true
}

async function confirmDispose() {
  if (!disposeForm.disposal_method) {
    ElMessage.warning('请选择处置方式')
    return
  }
  await http.post(`/asset/scrap/${disposeId.value}/dispose`, { ...disposeForm })
  ElMessage.success('已处置归档')
  disposeVisible.value = false
  reloadFn.value?.()
}

onMounted(async () => {
  disposalOptions.value = (await loadDict('disposal_method')) ?? []
})
</script>
