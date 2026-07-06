<template>
  <SystemPageCard title="审批配置" subtitle="维护审批流程与节点" :loading="loading">
    <template #actions>
      <el-button type="primary" @click="openFlowForm()">新增流程</el-button>
    </template>
    <el-table :data="flows" border stripe class="system-table" :height="flowTableHeight">
      <el-table-column prop="flow_code" label="流程编码" width="160" />
      <el-table-column prop="flow_name" label="流程名称" />
      <el-table-column prop="business_type" label="业务类型" width="140" />
      <el-table-column prop="is_active" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.is_active ? 'success' : 'info'" size="small">{{ row.is_active ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" @click="loadNodes(row)">节点</el-button>
            <el-button link @click="openFlowForm(row)">编辑</el-button>
            <el-button link type="danger" @click="removeFlow(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="currentFlow" class="approval-nodes-panel">
      <div class="approval-nodes-header">
        <span class="approval-nodes-title">审批节点 — {{ currentFlow.flow_name }}</span>
        <el-button type="primary" size="small" @click="openNodeForm()">新增节点</el-button>
      </div>
      <el-table :data="nodes" border stripe class="system-table" max-height="240">
        <el-table-column prop="node_order" label="顺序" width="80" />
        <el-table-column prop="node_name" label="节点名称" />
        <el-table-column prop="approver_role" label="审批角色" width="140" />
        <el-table-column prop="amount_threshold" label="金额阈值" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeNode(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!nodes.length" description="暂无审批节点，请点击上方新增" :image-size="64" />
    </div>

    <el-dialog v-model="flowVisible" title="审批流程" width="480px" destroy-on-close>
      <el-form :model="flowForm" label-width="100px">
        <el-form-item label="流程编码" required><el-input v-model="flowForm.flow_code" :disabled="!!flowForm.id" /></el-form-item>
        <el-form-item label="流程名称" required><el-input v-model="flowForm.flow_name" /></el-form-item>
        <el-form-item label="业务类型" required><el-input v-model="flowForm.business_type" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="flowForm.is_active" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flowVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFlow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="nodeVisible" title="审批节点" width="480px" destroy-on-close>
      <el-form :model="nodeForm" label-width="100px">
        <el-form-item label="顺序" required><el-input-number v-model="nodeForm.node_order" :min="1" /></el-form-item>
        <el-form-item label="节点名称" required><el-input v-model="nodeForm.node_name" /></el-form-item>
        <el-form-item label="审批角色" required><el-input v-model="nodeForm.approver_role" /></el-form-item>
        <el-form-item label="金额阈值"><el-input-number v-model="nodeForm.amount_threshold" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNode">保存</el-button>
      </template>
    </el-dialog>
  </SystemPageCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'

const tableHeight = useSystemTableHeight()
const flowTableHeight = computed(() => {
  const base = tableHeight.value
  return currentFlow.value ? Math.max(180, base - 300) : base
})

const loading = ref(false)

const flows = ref<any[]>([])
const nodes = ref<any[]>([])
const currentFlow = ref<any>(null)
const flowVisible = ref(false)
const nodeVisible = ref(false)
const flowForm = ref<any>({ is_active: true })
const nodeForm = ref<any>({ node_order: 1, amount_threshold: 0 })

onMounted(loadFlows)

async function loadFlows() {
  loading.value = true
  try {
    const { data } = await http.get('/system/approval-config/flows')
    if (data.code === 0) flows.value = data.data
  } finally {
    loading.value = false
  }
}

function openFlowForm(row?: any) {
  flowForm.value = row ? { ...row } : { flow_code: '', flow_name: '', business_type: '', is_active: true }
  flowVisible.value = true
}

async function saveFlow() {
  await http.post('/system/approval-config/flows', flowForm.value)
  ElMessage.success('保存成功')
  flowVisible.value = false
  loadFlows()
}

async function removeFlow(row: any) {
  await ElMessageBox.confirm('将删除流程及所有节点，是否继续？', '删除流程', { type: 'warning' })
  await http.delete(`/system/approval-config/flows/${row.id}`)
  currentFlow.value = null
  nodes.value = []
  loadFlows()
}

async function loadNodes(row: any) {
  currentFlow.value = row
  const { data } = await http.get(`/system/approval-config/flows/${row.id}`)
  if (data.code === 0) nodes.value = data.data.nodes || []
}

function openNodeForm() {
  nodeForm.value = { node_order: nodes.value.length + 1, node_name: '', approver_role: '', amount_threshold: 0 }
  nodeVisible.value = true
}

async function saveNode() {
  if (!currentFlow.value) return
  await http.post(`/system/approval-config/flows/${currentFlow.value.id}/nodes`, nodeForm.value)
  ElMessage.success('节点已保存')
  nodeVisible.value = false
  loadNodes(currentFlow.value)
}

async function removeNode(row: any) {
  await http.delete(`/system/approval-config/nodes/${row.id}`)
  loadNodes(currentFlow.value)
}
</script>
