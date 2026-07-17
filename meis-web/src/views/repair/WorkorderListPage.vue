<template>
  <div class="workflow-crud">
    <CrudPage
      ref="crudRef"
      :config="config"
      detail-mode
      hide-add
      delete-url="/repair/workorder"
      :operation-column-width="pageMode === 'handle' || pageMode === 'verify' ? 100 : 400"
      :hide-operation-column="pageMode === 'handle' || pageMode === 'verify'"
      :can-edit="canEditRow"
      :can-delete="canDeleteRow"
      @detail="openDetail"
      @add="openCreate"
    >
      <template #toolbar-extra>
        <el-button v-if="showCreate" type="primary" @click="openCreate">新增报修</el-button>
      </template>
      <template v-if="pageMode === 'verify'" #extra-columns>
        <el-table-column label="查看" width="72" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column label="验收" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('verify', row)" link type="success" @click.stop="openVerify(row)">验收</el-button>
          </template>
        </el-table-column>
        <el-table-column label="拒绝验收" width="88" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('verify', row)" link type="danger" @click.stop="openRejectVerify(row)">拒绝</el-button>
          </template>
        </el-table-column>
        <el-table-column label="变更记录" width="88" align="center">
          <template #default="{ row }">
            <el-button v-if="canRowChangeLog(row)" link @click.stop="openChangeLog(row)">变更记录</el-button>
          </template>
        </el-table-column>
      </template>
      <template v-else-if="pageMode === 'handle'" #extra-columns>
        <el-table-column label="查看" width="72" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column label="派工" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('dispatch', row)" link type="primary" @click.stop="openDispatch(row)">派工</el-button>
          </template>
        </el-table-column>
        <el-table-column label="抢单" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('grab', row)" link type="primary" @click.stop="doGrab(row)">抢单</el-button>
          </template>
        </el-table-column>
        <el-table-column label="接单" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('accept', row)" link type="primary" @click.stop="doAccept(row)">接单</el-button>
          </template>
        </el-table-column>
        <el-table-column label="转派" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('transfer', row)" link type="primary" @click.stop="openTransfer(row)">转派</el-button>
          </template>
        </el-table-column>
        <el-table-column label="添加进程" width="88" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('segment', row)" link type="primary" @click.stop="openAddSegment(row)">添加进程</el-button>
          </template>
        </el-table-column>
        <el-table-column label="开始维修" width="88" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('start', row)" link type="success" @click.stop="doStartRepair(row)">开始</el-button>
          </template>
        </el-table-column>
        <el-table-column label="完工" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('complete', row)" link type="warning" @click.stop="openComplete(row)">完工</el-button>
          </template>
        </el-table-column>
        <el-table-column label="挂起" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('suspend', row)" link @click.stop="doSuspend(row)">挂起</el-button>
          </template>
        </el-table-column>
        <el-table-column label="恢复" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('resume', row)" link type="success" @click.stop="doResume(row)">恢复</el-button>
          </template>
        </el-table-column>
        <el-table-column label="取消" width="72" align="center">
          <template #default="{ row }">
            <el-button v-if="canOnRow('cancel', row)" link type="danger" @click.stop="doCancel(row)">取消</el-button>
          </template>
        </el-table-column>
        <el-table-column label="变更记录" width="88" align="center">
          <template #default="{ row }">
            <el-button v-if="canRowChangeLog(row)" link @click.stop="openChangeLog(row)">变更记录</el-button>
          </template>
        </el-table-column>
      </template>
      <template v-else #row-actions="{ row }">
        <template v-if="pageMode === 'apply' || pageMode === 'all'">
          <el-button v-if="canRowSubmit(row)" link type="primary" @click.stop="doSubmit(row)">提交</el-button>
          <el-button v-if="canRowWithdraw(row)" link type="warning" @click.stop="doWithdraw(row)">撤回</el-button>
        </template>
        <template v-if="pageMode === 'all'">
          <el-button v-if="canOnRow('dispatch', row)" link type="primary" @click.stop="openDispatch(row)">派工</el-button>
          <el-button v-if="canOnRow('grab', row)" link type="primary" @click.stop="doGrab(row)">抢单</el-button>
          <el-button v-if="canOnRow('segment', row)" link type="primary" @click.stop="openAddSegment(row)">添加进程</el-button>
          <el-button v-if="canOnRow('cancel', row)" link type="danger" @click.stop="doCancel(row)">取消</el-button>
        </template>
        <el-button v-if="canRowChangeLog(row)" link @click.stop="openChangeLog(row)">变更记录</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="wo">
        <GroupedFormFields :table="config.table" :model="wo" :fields="formFields" />

        <FormSection v-if="wo.id" title="维修进程段" class="timeline-section">
          <div v-if="!processSegments.length" class="muted">
            暂无进程段。
            <span v-if="pageMode !== 'apply' && !isRepairEngineer">添加进程需当前登录账号为维修工程师（用户管理开启「是否维修工程师」），且为工单负责人或待派单可首段。</span>
          </div>
          <div v-for="seg in processSegments" :key="String(seg.id)" class="seg-row">
            <div>
              <strong>{{ seg.type_name }}</strong>
              <el-tag v-if="seg.open" size="small" type="success" style="margin-left: 8px">进行中</el-tag>
              <el-tag v-if="segmentConfirmStatus(seg) === 'confirmed'" size="small" type="info" style="margin-left: 8px">已确认</el-tag>
              <el-tag v-else-if="segmentConfirmStatus(seg) === 'system'" size="small" type="info" style="margin-left: 8px">已确认（系统）</el-tag>
              <el-tag v-else size="small" type="warning" style="margin-left: 8px">未确认</el-tag>
            </div>
            <div class="muted">
              {{ fmt(seg.started_at) }} ~ {{ seg.ended_at ? fmt(seg.ended_at) : '至今' }}
            </div>
            <div class="muted">
              确认状态：{{ segmentConfirmLabel(seg) }}
              · 确认人：{{ segmentConfirmPerson(seg) }}
              · 确认时间：{{ segmentConfirmTime(seg) }}
            </div>
            <div v-if="seg.remark || seg.verify_comment" class="muted">{{ seg.remark || seg.verify_comment }}</div>
            <div v-if="Array.isArray(seg.users) && seg.users.length" class="seg-users">
              <div v-for="(u, ui) in seg.users" :key="String(u.user_id ?? ui)" class="muted">
                工程师：{{ u.user_name || u.user_id }}{{ u.is_primary ? '（主责）' : '' }}
                <span v-if="u.work_content"> · {{ u.work_content }}</span>
                <span v-if="u.labor_cost != null && u.labor_cost !== ''"> · 人工费 {{ u.labor_cost }}</span>
              </div>
            </div>
            <div v-else-if="Array.isArray(seg.user_names) && seg.user_names.length" class="muted">
              工程师：{{ (seg.user_names as string[]).filter(Boolean).join('、') }}
            </div>
            <div v-else-if="seg.user_name" class="muted">工程师：{{ seg.user_name }}</div>
            <div v-if="seg.parts?.length" class="seg-parts">
              <div v-for="p in seg.parts" :key="String(p.id)" class="muted seg-part-line">
                配件：{{ p.part_name || p.spare_part_id }} × {{ p.quantity }}
                <span v-if="p.unit_price != null && p.unit_price !== ''"> · 单价 {{ p.unit_price }}</span>
                <span v-if="p.total_price != null && p.total_price !== ''"> · 金额 {{ p.total_price }}</span>
                <span v-if="p.supplier_name"> · {{ p.supplier_name }}</span>
                <template v-if="pageMode !== 'apply' && canEditSegment(seg)">
                  <el-button text type="primary" size="small" @click="openEditPart(seg, p)">改</el-button>
                  <el-button text type="danger" size="small" @click="doDeletePart(seg, p)">删</el-button>
                </template>
              </div>
            </div>
            <div v-else class="muted">配件：无</div>
            <template v-if="pageMode !== 'apply'">
              <el-button
                v-if="canEditSegment(seg)"
                text
                type="primary"
                size="small"
                @click="openEditSegment(seg)"
              >编辑</el-button>
              <el-button
                v-if="canEditSegment(seg)"
                text
                type="danger"
                size="small"
                @click="doDeleteSegment(seg)"
              >删除</el-button>
              <el-button
                v-if="segmentConfirmStatus(seg) === 'pending' && canConfirmSegment()"
                text
                type="warning"
                size="small"
                @click="doConfirmSegment(seg)"
              >确认固化</el-button>
              <el-button
                v-if="segmentConfirmStatus(seg) === 'pending' && seg.can_add_parts && can('segment')"
                text
                type="primary"
                size="small"
                @click="openAddPart(seg)"
              >添加配件</el-button>
            </template>
          </div>
        </FormSection>

        <FormSection v-if="wo.id && timelineData && pageMode !== 'apply'" title="工单时间轴" class="timeline-section">
          <div v-if="timelineData.summary" class="timeline-summary">
            <span>总停机 {{ fmtMin(timelineData.summary.downtimeMinutes) }}</span>
            <span>响应 {{ fmtMin(timelineData.summary.responseMinutes) }}</span>
            <span>维修 {{ fmtMin(timelineData.summary.repairMinutes) }}</span>
            <span>待验收 {{ fmtMin(timelineData.summary.pendingVerifyMinutes) }}</span>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="m in timelineData.milestones"
              :key="m.key"
              :timestamp="m.skipped ? '已跳过' : fmt(m.at)"
              :type="m.done ? (m.skipped ? 'info' : 'primary') : 'info'"
              placement="top"
            >
              <div>{{ m.label }}</div>
              <div v-if="m.skipReason" class="muted">{{ m.skipReason }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-if="timelineData.segments?.length" class="segments">
            <div class="seg-title">进程时段明细</div>
            <div v-for="(s, i) in timelineData.segments" :key="i" class="seg-row">
              {{ s.subStatusLabel }} · {{ fmt(s.start) }} ~ {{ s.end ? fmt(s.end) : '至今' }} · {{ fmtMin(s.minutes) }}
              <span v-if="s.remark" class="muted">（{{ s.remark }}）</span>
            </div>
          </div>
          <el-collapse v-if="timelineData.events?.length" class="event-collapse">
            <el-collapse-item title="全部事件流水" name="events">
              <div v-for="e in timelineData.events" :key="String(e.id)" class="event-row">
                <span class="event-time">{{ fmt(e.created_at) }}</span>
                <span>{{ e.event_label || e.event_type }}</span>
                <span v-if="e.remark" class="muted">· {{ e.remark }}</span>
              </div>
            </el-collapse-item>
          </el-collapse>
        </FormSection>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <template v-if="wo?.id && (pageMode === 'handle' || pageMode === 'all')">
          <el-button v-if="can('grab')" type="primary" @click="doGrab">抢单</el-button>
          <el-button v-if="can('start')" type="success" plain @click="doStartRepair">开始维修</el-button>
          <el-button v-if="can('accept')" @click="doAccept">接单</el-button>
          <el-button v-if="can('segment')" type="primary" plain @click="openAddSegment()">添加进程</el-button>
          <el-button v-if="can('transfer')" @click="openTransfer">转派</el-button>
          <el-button v-if="can('sub')" @click="openSubStatus">子状态</el-button>
          <el-button v-if="can('complete')" type="warning" @click="openComplete">完工</el-button>
          <el-button v-if="can('suspend')" @click="doSuspend">挂起</el-button>
          <el-button v-if="can('resume')" @click="doResume">恢复</el-button>
        </template>
        <template v-if="pageMode === 'verify' && wo?.id">
          <el-button v-if="can('verify')" type="success" @click="openVerify()">验收</el-button>
          <el-button v-if="can('verify')" type="danger" @click="openRejectVerify()">拒绝验收</el-button>
        </template>
        <el-button v-if="editable && !wo?.id" type="primary" @click="saveDraft">保存草稿</el-button>
        <el-button v-if="editable && wo?.id && status === 'draft'" type="primary" plain @click="saveDraft">保存</el-button>
      </template>
    </AppModal>

    <EntityChangeHistoryDrawer
      v-model="changeLogVisible"
      entity-type="repair_workorder"
      :entity-id="changeLogEntityId"
    />

    <AppModal v-model="segmentVisible" title="添加维修进程" size="lg">
      <el-form label-width="120px">
        <el-form-item label="进程类型" required>
          <el-select v-model="actionForm.processTypeId" placeholder="请选择" style="width: 100%">
            <el-option v-for="t in addableTypes" :key="String(t.id)" :label="String(t.type_name)" :value="String(t.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="维修工程师" required>
          <div class="seg-engineer-field">
            <div v-for="(row, idx) in engineerRows" :key="idx" class="seg-engineer-row">
              <RefSelect
                v-model="row.userId"
                link-table="repair_engineer"
                placeholder="选择工程师"
                style="width: 160px"
              />
              <el-input
                v-model="row.workContent"
                placeholder="工作内容（选填）"
                style="flex: 1; min-width: 120px"
              />
              <el-input-number
                v-model="row.laborCost"
                :min="0"
                :precision="2"
                :controls="false"
                placeholder="人工费"
                style="width: 110px"
              />
              <el-checkbox v-model="row.isPrimary" @change="onPrimaryChange(idx)">主责</el-checkbox>
              <el-button text type="danger" :disabled="engineerRows.length <= 1" @click="engineerRows.splice(idx, 1)">删</el-button>
            </div>
            <el-button text type="primary" @click="addEngineerRow">+ 添加工程师</el-button>
          </div>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="actionForm.startedAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <div class="seg-end-time">
            <el-checkbox v-model="actionForm.enableEndedAt">填写结束时间（补录）</el-checkbox>
            <el-date-picker
              v-model="actionForm.endedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm:ss"
              placeholder="结束时间"
              style="width: 100%"
              :disabled="!actionForm.enableEndedAt"
            />
          </div>
        </el-form-item>
        <el-form-item v-if="selectedAddableType?.can_add_parts" label="配件">
          <div v-for="(row, idx) in segmentPartRows" :key="idx" class="seg-part-form-row">
            <RefSelect
              v-model="row.sparePartId"
              link-table="spare_part"
              placeholder="配件"
              style="flex: 1; min-width: 160px"
              @update:model-value="(v) => onSegmentPartSelected(row, v)"
            />
            <el-input-number v-model="row.quantity" :min="1" :max="9999" style="width: 110px" />
            <el-input-number
              v-model="row.unitPrice"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="单价"
              style="width: 100px"
            />
            <el-input :model-value="formatPartAmount(row)" disabled placeholder="金额" style="width: 100px" />
            <RefSelect v-model="row.supplierId" link-table="supplier" placeholder="供应商" style="width: 140px" />
            <el-button text type="danger" @click="segmentPartRows.splice(idx, 1)">删</el-button>
          </div>
          <el-button text type="primary" @click="addEmptyPartRow(segmentPartRows)">+ 添加配件</el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.segmentRemark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="segmentVisible = false">取消</el-button>
        <el-button type="primary" @click="doAddSegment">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="partVisible" title="进程段添加配件" size="md">
      <el-form label-width="80px">
        <el-form-item label="配件" required>
          <RefSelect
            v-model="partForm.sparePartId"
            link-table="spare_part"
            placeholder="请选择配件"
            @update:model-value="(v) => onSegmentPartSelected(partForm as SegmentPartFormRow, v)"
          />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="partForm.quantity" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="partForm.unitPrice" :min="0" :precision="2" :controls="false" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input :model-value="formatPartAmount(partForm)" disabled />
        </el-form-item>
        <el-form-item label="供应商">
          <RefSelect v-model="partForm.supplierId" link-table="supplier" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partVisible = false">取消</el-button>
        <el-button type="primary" @click="doAddPart">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="editSegmentVisible" title="编辑维修进程" size="lg">
      <el-form label-width="120px">
        <el-form-item label="进程类型">
          <el-input :model-value="String(editingSegment?.type_name ?? '')" disabled />
        </el-form-item>
        <el-form-item label="维修工程师" required>
          <div class="seg-engineer-field">
            <div v-for="(row, idx) in engineerRows" :key="idx" class="seg-engineer-row">
              <RefSelect
                v-model="row.userId"
                link-table="repair_engineer"
                placeholder="选择工程师"
                style="width: 160px"
              />
              <el-input
                v-model="row.workContent"
                placeholder="工作内容（选填）"
                style="flex: 1; min-width: 120px"
              />
              <el-input-number
                v-model="row.laborCost"
                :min="0"
                :precision="2"
                :controls="false"
                placeholder="人工费"
                style="width: 110px"
              />
              <el-checkbox v-model="row.isPrimary" @change="onPrimaryChange(idx)">主责</el-checkbox>
              <el-button text type="danger" :disabled="engineerRows.length <= 1" @click="engineerRows.splice(idx, 1)">删</el-button>
            </div>
            <el-button text type="primary" @click="addEngineerRow">+ 添加工程师</el-button>
          </div>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="actionForm.startedAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <div class="seg-end-time">
            <el-checkbox v-model="actionForm.enableEndedAt">填写结束时间</el-checkbox>
            <el-date-picker
              v-model="actionForm.endedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm:ss"
              placeholder="结束时间"
              style="width: 100%"
              :disabled="!actionForm.enableEndedAt"
            />
          </div>
        </el-form-item>
        <el-form-item v-if="editingCanAddParts" label="配件">
          <div v-for="(row, idx) in editSegmentPartRows" :key="idx" class="seg-part-form-row">
            <RefSelect
              v-model="row.sparePartId"
              link-table="spare_part"
              placeholder="配件"
              style="flex: 1; min-width: 160px"
              :disabled="Boolean(row.id)"
              @update:model-value="(v) => onSegmentPartSelected(row, v)"
            />
            <el-input-number v-model="row.quantity" :min="1" :max="9999" style="width: 110px" />
            <el-input-number
              v-model="row.unitPrice"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="单价"
              style="width: 100px"
            />
            <el-input :model-value="formatPartAmount(row)" disabled placeholder="金额" style="width: 100px" />
            <RefSelect v-model="row.supplierId" link-table="supplier" placeholder="供应商" style="width: 140px" />
            <el-button text type="danger" @click="editSegmentPartRows.splice(idx, 1)">删</el-button>
          </div>
          <el-button
            text
            type="primary"
            @click="addEmptyPartRow(editSegmentPartRows)"
          >+ 添加配件</el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.segmentRemark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editSegmentVisible = false">取消</el-button>
        <el-button type="primary" @click="doSaveEditSegment">保存</el-button>
      </template>
    </AppModal>

    <AppModal v-model="partEditVisible" title="编辑配件" size="md">
      <el-form label-width="80px">
        <el-form-item label="配件">
          <el-input :model-value="partEditForm.partName" disabled />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="partEditForm.quantity" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="partEditForm.unitPrice" :min="0" :precision="2" :controls="false" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input :model-value="formatPartAmount(partEditForm)" disabled />
        </el-form-item>
        <el-form-item label="供应商">
          <RefSelect v-model="partEditForm.supplierId" link-table="supplier" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partEditVisible = false">取消</el-button>
        <el-button type="primary" @click="doSaveEditPart">保存</el-button>
      </template>
    </AppModal>

    <AppModal v-model="dispatchVisible" title="派工 / 指派工程师" size="md">
      <el-form label-width="100px">
        <el-form-item label="工程师" required>
          <RefSelect v-model="actionForm.userId" link-table="repair_engineer" placeholder="请选择工程师" />
        </el-form-item>
        <el-form-item label="派工并开工">
          <el-switch v-model="actionForm.startRepair" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" @click="doDispatch">确认</el-button>
      </template>
    </AppModal>

    <AppModal v-model="transferVisible" title="转派工程师" size="md">
      <el-form label-width="120px">
        <el-form-item label="目标工程师" required>
          <RefSelect v-model="actionForm.userId" link-table="repair_engineer" placeholder="请选择工程师" />
        </el-form-item>
        <el-form-item v-if="wo?.status === 'repairing' || wo?.status === 'verify_rejected'" label="保持当前状态">
          <el-switch v-model="actionForm.keepRepairing" />
        </el-form-item>
        <el-form-item label="转派原因">
          <el-input v-model="actionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="doTransfer">确认转派</el-button>
      </template>
    </AppModal>

    <AppModal v-model="completeVisible" title="维修完工" size="md">
      <el-form label-width="100px">
        <el-form-item label="处理方案">
          <el-input v-model="actionForm.solution" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="完工方式">
          <el-radio-group v-model="actionForm.skipVerify">
            <el-radio :value="false">提交验收</el-radio>
            <el-radio v-if="status !== 'verify_rejected'" :value="true">直接结案（跳过验收）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" @click="doComplete">确认完工</el-button>
      </template>
    </AppModal>

    <AppModal v-model="subVisible" title="更新维修子状态" size="md">
      <el-form label-width="100px">
        <el-form-item label="子状态" required>
          <el-select v-model="actionForm.subStatus" style="width: 100%">
            <el-option v-for="o in subOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="actionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" @click="doSubStatus">确认</el-button>
      </template>
    </AppModal>
    <AppModal
      v-model="verifyVisible"
      :title="actionForm.verifyResult === 'fail' ? '拒绝验收' : '维修验收'"
      size="md"
    >
      <el-form label-width="100px">
        <el-form-item label="验收结果" required>
          <el-radio-group v-model="actionForm.verifyResult">
            <el-radio value="pass">通过</el-radio>
            <el-radio value="fail">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="actionForm.verifyResult === 'fail'" label="拒绝原因" required>
          <el-input v-model="actionForm.verifyComment" type="textarea" :rows="3" placeholder="请填写拒绝验收原因" />
        </el-form-item>
        <el-form-item v-else label="验收意见">
          <el-input v-model="actionForm.verifyComment" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="actionForm.verifyResult === 'pass'" label="满意度">
          <el-rate v-model="actionForm.satisfactionRating" :max="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button
          :type="actionForm.verifyResult === 'fail' ? 'danger' : 'primary'"
          @click="submitVerify"
        >{{ actionForm.verifyResult === 'fail' ? '确认拒绝' : '确认验收' }}</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import EntityChangeHistoryDrawer from '@/components/EntityChangeHistoryDrawer.vue'
import type { PageConfig } from '@/config/pageRegistry'
import {
  REPAIR_APPLY_FILTERS,
  REPAIR_HANDLE_FILTERS,
  REPAIR_VERIFY_FILTERS
} from '@/config/repairListFilters'
import { getSchema } from '@/config/pageSchemas'

const route = useRoute()
const pageMode = computed(() => {
  const path = route.path
  if (path.endsWith('/apply')) return 'apply'
  if (path.endsWith('/handle')) return 'handle'
  if (path.endsWith('/verify')) return 'verify'
  return 'all'
})

const modeTitles: Record<string, string> = {
  apply: '报修申请',
  handle: '维修处理',
  verify: '维修验收',
  all: '维修工单'
}

const repairListFilters = computed(() => {
  switch (pageMode.value) {
    case 'apply':
      return REPAIR_APPLY_FILTERS
    case 'handle':
      return REPAIR_HANDLE_FILTERS
    case 'verify':
      return REPAIR_VERIFY_FILTERS
    default:
      return REPAIR_APPLY_FILTERS
  }
})

const config = computed<PageConfig>(() => ({
  title: modeTitles[pageMode.value] ?? '维修工单',
  apiBase: '/repair',
  table: 'repair_workorder',
  listPageUrl: '/repair/workorder/page',
  listMode: pageMode.value === 'all' ? undefined : pageMode.value,
  listFilters: pageMode.value === 'all' ? REPAIR_APPLY_FILTERS : repairListFilters.value
}))

const showCreate = computed(() => pageMode.value === 'apply' || pageMode.value === 'all')
const isRepairEngineer = ref(false)
const auth = useAuthStore()
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const changeLogVisible = ref(false)
const changeLogEntityId = ref('')
const wo = ref<Record<string, unknown> | null>(null)
const timelineData = ref<{
  summary?: Record<string, number | string>
  milestones?: Array<Record<string, unknown>>
  segments?: Array<Record<string, unknown>>
  events?: Array<Record<string, unknown>>
} | null>(null)

const processSegments = ref<Array<Record<string, unknown>>>([])
const addableTypes = ref<Array<Record<string, unknown>>>([])
const segmentVisible = ref(false)
const editSegmentVisible = ref(false)
const editingSegment = ref<Record<string, unknown> | null>(null)
const editingCanAddParts = ref(false)
const originalEditPartIds = ref<string[]>([])
type SegmentPartFormRow = {
  id?: string
  sparePartId: string
  quantity: number
  unitPrice?: number
  supplierId: string
}
const editSegmentPartRows = ref<SegmentPartFormRow[]>([])
const partVisible = ref(false)
const partEditVisible = ref(false)
const partForm = reactive({
  segmentId: '',
  sparePartId: '',
  quantity: 1,
  unitPrice: undefined as number | undefined,
  supplierId: ''
})
const partEditForm = reactive({
  segmentId: '',
  partId: '',
  partName: '',
  quantity: 1,
  unitPrice: undefined as number | undefined,
  supplierId: ''
})
const segmentPartRows = ref<SegmentPartFormRow[]>([])

function formatPartAmount(row: { quantity?: number; unitPrice?: number }) {
  const q = Number(row.quantity)
  const p = row.unitPrice
  if (p == null || Number.isNaN(Number(p)) || Number.isNaN(q)) return ''
  return (q * Number(p)).toFixed(2)
}

function addEmptyPartRow(rows: SegmentPartFormRow[] | { value: SegmentPartFormRow[] }) {
  const list = Array.isArray(rows) ? rows : rows.value
  list.push({ sparePartId: '', quantity: 1, unitPrice: undefined, supplierId: '' })
}

async function onSegmentPartSelected(row: SegmentPartFormRow, partId: unknown) {
  const id = partId != null && String(partId).trim() !== '' ? String(partId) : ''
  row.sparePartId = id
  if (!id) {
    row.unitPrice = undefined
    row.supplierId = ''
    return
  }
  try {
    const { data } = await http.get(`/repair/spare_part/${id}`)
    const part = (data.data ?? data) as Record<string, unknown>
    if (part && typeof part === 'object') {
      if (part.unit_price != null && part.unit_price !== '') {
        row.unitPrice = Number(part.unit_price)
      }
      if (part.supplier_id != null && String(part.supplier_id).trim() !== '') {
        row.supplierId = String(part.supplier_id)
      }
    }
  } catch {
    /* 带出失败时仍可手工填单价/供应商 */
  }
}
const engineerRows = ref<Array<{ userId: string; workContent: string; laborCost?: number; isPrimary: boolean }>>([])
const dispatchVisible = ref(false)
const transferVisible = ref(false)
const completeVisible = ref(false)
const subVisible = ref(false)
const verifyVisible = ref(false)
const actionForm = reactive({
  userId: '' as string,
  segmentUserId: '' as string,
  segmentUserIds: [] as string[],
  editEngineers: false,
  startRepair: false,
  keepRepairing: false,
  skipVerify: false,
  solution: '',
  remark: '',
  segmentRemark: '',
  processTypeId: '',
  startedAt: '',
  endedAt: '',
  enableEndedAt: false,
  subStatus: 'internal',
  verifyResult: 'pass' as 'pass' | 'fail',
  verifyComment: '',
  satisfactionRating: 5
})

function addEngineerRow() {
  engineerRows.value.push({ userId: '', workContent: '', laborCost: undefined, isPrimary: false })
}

function onPrimaryChange(idx: number) {
  if (!engineerRows.value[idx]?.isPrimary) return
  engineerRows.value.forEach((r, i) => {
    if (i !== idx) r.isPrimary = false
  })
}

function resetEngineerRows(defaultUserId = '') {
  engineerRows.value = [{
    userId: defaultUserId,
    workContent: '',
    laborCost: undefined,
    isPrimary: true
  }]
}

const subOptions = [
  { value: 'internal', label: '院内维修' },
  { value: 'external', label: '院外维修' },
  { value: 'waiting_parts', label: '等待配件' },
  { value: 'waiting_approval', label: '待审批' },
  { value: 'on_site', label: '已到场' },
  { value: 'diagnosing', label: '诊断中' },
  { value: 'testing', label: '调试中' }
]

const status = computed(() => String(wo.value?.status ?? ''))
const editable = computed(() => !wo.value?.id || status.value === 'draft')
const selectedAddableType = computed(() =>
  addableTypes.value.find((t) => String(t.id) === actionForm.processTypeId)
)

function rowStatus(row: Record<string, unknown>) {
  return String(row.status ?? '')
}

function isCancelledStatus(s: string) {
  return s === 'cancelled'
}

function isHandleReadOnlyStatus(s: string) {
  return ['pending_verify', 'verified', 'closed'].includes(s)
}

function isApplyScope() {
  return pageMode.value === 'apply' || pageMode.value === 'all'
}

function canWithdrawRow(row: Record<string, unknown>) {
  if (isCancelledStatus(rowStatus(row))) return false
  if (rowStatus(row) !== 'reported') return false
  return !row.assigned_user_id && !row.dispatch_started_at && !row.assigned_at
    && !row.accepted_at && !row.repair_start_time && !row.response_time
}

function canEditRow(row: Record<string, unknown>) {
  return rowStatus(row) === 'draft'
}

function canDeleteRow(row: Record<string, unknown>) {
  return rowStatus(row) === 'draft' && isApplyScope()
}

function canRowSubmit(row: Record<string, unknown>) {
  return rowStatus(row) === 'draft' && isApplyScope()
}

function canRowWithdraw(row: Record<string, unknown>) {
  return canWithdrawRow(row) && isApplyScope()
}

function canRowChangeLog(row: Record<string, unknown>) {
  return Boolean(row.id)
}

const APPLY_FORM_GROUPS = new Set(['basic', 'remark'])

const canWithdraw = computed(() => canWithdrawRow(wo.value ?? {}))
const modalTitle = computed(() => {
  if (!wo.value?.id) return '维修工单 新增'
  if (status.value === 'draft') return '维修工单 草稿'
  return editable.value ? '维修工单 编辑' : '维修工单 详情'
})
const formFields = computed(() => {
  let fields = getSchema('repair_workorder')
  if (pageMode.value === 'apply') {
    fields = fields.filter((f) => APPLY_FORM_GROUPS.has(f.group ?? 'basic'))
  }
  if (editable.value) return fields
  return fields.map((f) => ({ ...f, readonly: true }))
})

function isUnassignedWo(row?: Record<string, unknown> | null) {
  const target = row ?? wo.value
  if (!target) return true
  const id = target.assigned_user_id
  return id == null || String(id).trim() === ''
}

function isOwnerWo(row?: Record<string, unknown> | null) {
  const target = row ?? wo.value
  const uid = auth.user?.userId
  if (!target || !uid) return false
  return String(target.assigned_user_id ?? '') === String(uid)
}

function can(action: string, row?: Record<string, unknown> | null) {
  const target = row ?? wo.value
  const s = String(target?.status ?? status.value)
  const mode = pageMode.value
  if (isCancelledStatus(s)) return false
  if (action === 'submit') return s === 'draft' && (mode === 'apply' || mode === 'all')
  if (action === 'withdraw') {
    return canWithdrawRow(target ?? {}) && (mode === 'apply' || mode === 'all')
  }
  if (action === 'delete') return s === 'draft' && (mode === 'apply' || mode === 'all')
  if (mode === 'apply') return false
  if (mode === 'verify') {
    return action === 'verify' && s === 'pending_verify'
  }
  if (mode === 'handle' && isHandleReadOnlyStatus(s)) return false
  if (mode === 'handle' && action === 'verify') return false

  const unassigned = isUnassignedWo(target)
  const owner = isOwnerWo(target)

  switch (action) {
    case 'grab':
      return isRepairEngineer.value
        && (s === 'reported' || s === 'dispatching')
        && unassigned
    case 'dispatch':
      if (s === 'reported' || s === 'dispatching') return unassigned
      return ['pending_accept', 'accepted', 'repairing'].includes(s)
    case 'start':
      return ['pending_accept', 'accepted'].includes(s) && owner
    case 'accept':
      return ['pending_accept', 'dispatching'].includes(s) && owner && !unassigned
    case 'segment':
      if (!isRepairEngineer.value) return false
      if (!['reported', 'dispatching', 'pending_accept', 'accepted', 'repairing', 'suspended', 'verify_rejected'].includes(s)) return false
      if (unassigned && (s === 'reported' || s === 'dispatching')) return true
      return owner
    case 'transfer':
      return ['dispatching', 'pending_accept', 'accepted', 'repairing', 'verify_rejected'].includes(s) && owner
    case 'sub':
      return (s === 'repairing' || s === 'verify_rejected') && owner
    case 'complete':
      return (s === 'repairing' || s === 'verify_rejected') && owner
    case 'verify':
      return s === 'pending_verify'
    case 'suspend':
      return s === 'repairing' && owner
    case 'resume':
      return s === 'suspended' && owner
    case 'cancel':
      return mode === 'handle' || mode === 'all'
        ? !['draft', 'closed', 'cancelled', 'verified', 'pending_verify'].includes(s)
        : !['draft', 'closed', 'cancelled', 'verified'].includes(s)
    default:
      return false
  }
}

function canOnRow(action: string, row: Record<string, unknown>) {
  return can(action, row)
}

function fmt(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

function fmtMin(v: unknown) {
  const n = Number(v ?? 0)
  if (!n) return '0m'
  if (n < 60) return `${n}m`
  const h = Math.floor(n / 60)
  const m = n % 60
  return m ? `${h}h${m}m` : `${h}h`
}

function nowText() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function openCreate() {
  wo.value = {
    reporter_id: auth.user?.userId ?? '',
    report_time: nowText(),
    report_method: 'web',
    urgency_level: 'normal'
  }
  timelineData.value = null
  processSegments.value = []
  visible.value = true
}

async function loadSegments() {
  if (!wo.value?.id) {
    processSegments.value = []
    return
  }
  const { data } = await http.get(`/repair/workorder/${wo.value.id}/segments`)
  processSegments.value = data.data ?? []
}

async function loadAddableTypes() {
  if (!wo.value?.id) return
  const { data } = await http.get('/repair/process-type/addable', {
    params: { workorderId: wo.value.id, status: status.value }
  })
  addableTypes.value = data.data ?? []
}

async function openAddSegment(row?: Record<string, unknown>) {
  if (row?.id) {
    await openDetail(row)
  }
  if (!wo.value?.id) return
  if (!can('segment')) {
    ElMessage.warning(
      isRepairEngineer.value
        ? '当前账号不是该工单负责人，无法添加进程'
        : '添加维修进程需当前登录账号为「维修工程师」（请在用户管理开启）'
    )
    return
  }
  actionForm.processTypeId = ''
  actionForm.segmentRemark = ''
  const defaultEng = String(wo.value.assigned_user_id ?? auth.user?.userId ?? '')
  actionForm.segmentUserId = defaultEng
  actionForm.segmentUserIds = defaultEng ? [defaultEng] : []
  actionForm.editEngineers = false
  resetEngineerRows(defaultEng)
  actionForm.startedAt = nowText()
  actionForm.endedAt = ''
  actionForm.enableEndedAt = false
  segmentPartRows.value = []
  await loadAddableTypes()
  if (!addableTypes.value.length) {
    ElMessage.warning('暂无可添加的进程类型，请确认已迁移并写入进程类型种子（重启 meis-tenant）')
  }
  segmentVisible.value = true
}

async function doAddSegment() {
  if (!wo.value?.id || !actionForm.processTypeId) {
    ElMessage.warning('请选择进程类型')
    return
  }
  const engineers = engineerRows.value
    .filter((r) => r.userId)
    .map((r, i, arr) => ({
      userId: r.userId,
      workContent: r.workContent || undefined,
      laborCost: r.laborCost,
      isPrimary: r.isPrimary || (!arr.some((x) => x.isPrimary) && i === 0)
    }))
  if (!engineers.length) {
    ElMessage.warning('请选择维修工程师')
    return
  }
  if (!engineers.some((e) => e.isPrimary)) {
    engineers[0].isPrimary = true
  }
  if (!actionForm.startedAt) {
    ElMessage.warning('请填写开始时间')
    return
  }
  if (actionForm.enableEndedAt && !actionForm.endedAt) {
    ElMessage.warning('请填写结束时间')
    return
  }
  const parts = segmentPartRows.value
    .filter((r) => r.sparePartId)
    .map((r) => ({
      spare_part_id: r.sparePartId,
      quantity: r.quantity,
      unit_price: r.unitPrice,
      supplier_id: r.supplierId || undefined
    }))
  const primary = engineers.find((e) => e.isPrimary) ?? engineers[0]
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/segments`, {
    processTypeId: actionForm.processTypeId,
    engineers,
    userIds: engineers.map((e) => e.userId),
    userId: primary.userId,
    startedAt: actionForm.startedAt,
    enableEndedAt: actionForm.enableEndedAt,
    endedAt: actionForm.enableEndedAt ? actionForm.endedAt : undefined,
    remark: actionForm.segmentRemark,
    parts
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '添加失败')
    return
  }
  segmentVisible.value = false
  ElMessage.success('进程段已添加')
  await refresh()
}

function canConfirmSegment() {
  if (pageMode.value === 'apply' || pageMode.value === 'verify') return false
  const s = status.value
  return !['draft', 'cancelled', 'closed'].includes(s)
}

function canEditSegment(seg: Record<string, unknown>) {
  return segmentConfirmStatus(seg) === 'pending' && can('segment')
}

function segmentConfirmStatus(seg: Record<string, unknown>) {
  if (seg.confirmed_at) return 'confirmed'
  if (seg.auto_created || seg.confirmed) return 'system'
  return 'pending'
}

function segmentConfirmLabel(seg: Record<string, unknown>) {
  const st = segmentConfirmStatus(seg)
  if (st === 'confirmed') return '已确认'
  if (st === 'system') return '已确认（系统）'
  return '未确认'
}

function segmentConfirmPerson(seg: Record<string, unknown>) {
  if (seg.confirmed_by_name) return String(seg.confirmed_by_name)
  if (segmentConfirmStatus(seg) === 'system') return '系统'
  return '—'
}

function segmentConfirmTime(seg: Record<string, unknown>) {
  if (seg.confirmed_at) return fmt(seg.confirmed_at)
  if (segmentConfirmStatus(seg) === 'system') return fmt(seg.started_at) || '—'
  return '—'
}

async function doConfirmSegment(seg: Record<string, unknown>) {
  if (!wo.value?.id || !seg.id) return
  await ElMessageBox.confirm('确认后该进程段将固化，不可再修改工程师与配件。是否确认？', '确认固化', {
    type: 'warning'
  })
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/segments/${seg.id}/confirm`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '确认失败')
    return
  }
  ElMessage.success('进程段已确认固化')
  await loadSegments()
}

function openAddPart(seg: Record<string, unknown>) {
  partForm.segmentId = String(seg.id)
  partForm.sparePartId = ''
  partForm.quantity = 1
  partForm.unitPrice = undefined
  partForm.supplierId = ''
  partVisible.value = true
}

async function doAddPart() {
  if (!wo.value?.id || !partForm.segmentId || !partForm.sparePartId) {
    ElMessage.warning('请选择配件')
    return
  }
  const body: Record<string, unknown> = {
    spare_part_id: partForm.sparePartId,
    quantity: partForm.quantity
  }
  if (partForm.unitPrice != null) body.unit_price = partForm.unitPrice
  if (partForm.supplierId) body.supplier_id = partForm.supplierId
  const { data } = await http.post(
    `/repair/workorder/${wo.value.id}/segments/${partForm.segmentId}/parts`,
    body
  )
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '添加失败')
    return
  }
  partVisible.value = false
  ElMessage.success('配件已添加')
  await loadSegments()
}

function openEditSegment(seg: Record<string, unknown>) {
  if (!canEditSegment(seg)) return
  editingSegment.value = seg
  editingCanAddParts.value = Boolean(seg.can_add_parts)
  actionForm.segmentRemark = seg.remark != null ? String(seg.remark) : ''
  actionForm.startedAt = fmt(seg.started_at)
  const ended = fmt(seg.ended_at)
  actionForm.endedAt = ended
  actionForm.enableEndedAt = Boolean(ended)
  const users = Array.isArray(seg.users) ? seg.users as Array<Record<string, unknown>> : []
  if (users.length) {
    engineerRows.value = users.map((u) => ({
      userId: String(u.user_id ?? ''),
      workContent: u.work_content != null ? String(u.work_content) : '',
      laborCost: u.labor_cost != null && u.labor_cost !== '' ? Number(u.labor_cost) : undefined,
      isPrimary: Boolean(u.is_primary)
    }))
    if (!engineerRows.value.some((r) => r.isPrimary) && engineerRows.value.length) {
      engineerRows.value[0].isPrimary = true
    }
  } else {
    resetEngineerRows(String(seg.user_id ?? wo.value?.assigned_user_id ?? auth.user?.userId ?? ''))
  }
  const parts = Array.isArray(seg.parts) ? seg.parts as Array<Record<string, unknown>> : []
  originalEditPartIds.value = parts.map((p) => String(p.id)).filter(Boolean)
  editSegmentPartRows.value = parts.map((p) => ({
    id: p.id != null ? String(p.id) : undefined,
    sparePartId: String(p.spare_part_id ?? ''),
    quantity: Number(p.quantity ?? 1) || 1,
    unitPrice: p.unit_price != null && p.unit_price !== '' ? Number(p.unit_price) : undefined,
    supplierId: p.supplier_id != null ? String(p.supplier_id) : ''
  }))
  editSegmentVisible.value = true
}

async function doSaveEditSegment() {
  if (!wo.value?.id || !editingSegment.value?.id) return
  const engineers = engineerRows.value
    .filter((r) => r.userId)
    .map((r, i, arr) => ({
      userId: r.userId,
      workContent: r.workContent || undefined,
      laborCost: r.laborCost,
      isPrimary: r.isPrimary || (!arr.some((x) => x.isPrimary) && i === 0)
    }))
  if (!engineers.length) {
    ElMessage.warning('请至少保留一名维修工程师')
    return
  }
  if (!engineers.some((e) => e.isPrimary)) {
    engineers[0].isPrimary = true
  }
  if (!actionForm.startedAt) {
    ElMessage.warning('请填写开始时间')
    return
  }
  if (actionForm.enableEndedAt && !actionForm.endedAt) {
    ElMessage.warning('请填写结束时间')
    return
  }
  if (editingCanAddParts.value) {
    const invalid = editSegmentPartRows.value.some((r) => r.sparePartId && (!r.quantity || r.quantity < 1))
    if (invalid) {
      ElMessage.warning('配件数量须不少于 1')
      return
    }
  }
  const segmentId = String(editingSegment.value.id)
  const { data } = await http.put(`/repair/workorder/${wo.value.id}/segments/${segmentId}`, {
    remark: actionForm.segmentRemark,
    startedAt: actionForm.startedAt,
    enableEndedAt: actionForm.enableEndedAt,
    endedAt: actionForm.enableEndedAt ? actionForm.endedAt : null,
    engineers
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }
  if (editingCanAddParts.value) {
    const keepIds = new Set(
      editSegmentPartRows.value.filter((r) => r.id && r.sparePartId).map((r) => String(r.id))
    )
    for (const oldId of originalEditPartIds.value) {
      if (!keepIds.has(oldId)) {
        const del = await http.delete(`/repair/workorder/${wo.value.id}/segments/${segmentId}/parts/${oldId}`)
        if (del.data.code !== 0 && del.data.code !== 200) {
          ElMessage.error(del.data.message || '删除配件失败')
          await loadSegments()
          return
        }
      }
    }
    for (const row of editSegmentPartRows.value) {
      if (!row.sparePartId) continue
      if (row.id) {
        const put = await http.put(
          `/repair/workorder/${wo.value.id}/segments/${segmentId}/parts/${row.id}`,
          {
            quantity: row.quantity,
            unit_price: row.unitPrice ?? null,
            supplier_id: row.supplierId || null
          }
        )
        if (put.data.code !== 0 && put.data.code !== 200) {
          ElMessage.error(put.data.message || '更新配件失败')
          await loadSegments()
          return
        }
      } else {
        const post = await http.post(
          `/repair/workorder/${wo.value.id}/segments/${segmentId}/parts`,
          {
            spare_part_id: row.sparePartId,
            quantity: row.quantity,
            unit_price: row.unitPrice,
            supplier_id: row.supplierId || undefined
          }
        )
        if (post.data.code !== 0 && post.data.code !== 200) {
          ElMessage.error(post.data.message || '添加配件失败')
          await loadSegments()
          return
        }
      }
    }
  }
  editSegmentVisible.value = false
  ElMessage.success('进程段已更新')
  await refresh()
}

async function doDeleteSegment(seg: Record<string, unknown>) {
  if (!wo.value?.id || !seg.id || !canEditSegment(seg)) return
  await ElMessageBox.confirm('确认删除该未确认进程段？删除后不会重开上一段结束时间。', '删除进程段', {
    type: 'warning'
  })
  const { data } = await http.delete(`/repair/workorder/${wo.value.id}/segments/${seg.id}`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '删除失败')
    return
  }
  ElMessage.success('进程段已删除')
  await refresh()
}

function openEditPart(seg: Record<string, unknown>, part: Record<string, unknown>) {
  if (!canEditSegment(seg) || !part.id) return
  partEditForm.segmentId = String(seg.id)
  partEditForm.partId = String(part.id)
  partEditForm.partName = String(part.part_name || part.spare_part_id || '')
  partEditForm.quantity = Number(part.quantity ?? 1) || 1
  partEditForm.unitPrice = part.unit_price != null && part.unit_price !== '' ? Number(part.unit_price) : undefined
  partEditForm.supplierId = part.supplier_id != null ? String(part.supplier_id) : ''
  partEditVisible.value = true
}

async function doSaveEditPart() {
  if (!wo.value?.id || !partEditForm.segmentId || !partEditForm.partId) return
  if (!partEditForm.quantity || partEditForm.quantity < 1) {
    ElMessage.warning('请填写数量')
    return
  }
  const { data } = await http.put(
    `/repair/workorder/${wo.value.id}/segments/${partEditForm.segmentId}/parts/${partEditForm.partId}`,
    {
      quantity: partEditForm.quantity,
      unit_price: partEditForm.unitPrice ?? null,
      supplier_id: partEditForm.supplierId || null
    }
  )
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }
  partEditVisible.value = false
  ElMessage.success('配件已更新')
  await loadSegments()
}

async function doDeletePart(seg: Record<string, unknown>, part: Record<string, unknown>) {
  if (!wo.value?.id || !seg.id || !part.id || !canEditSegment(seg)) return
  await ElMessageBox.confirm('确认删除该配件明细？', '删除配件', { type: 'warning' })
  const { data } = await http.delete(
    `/repair/workorder/${wo.value.id}/segments/${seg.id}/parts/${part.id}`
  )
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '删除失败')
    return
  }
  ElMessage.success('配件已删除')
  await loadSegments()
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/repair/workorder/${row.id}`)
  wo.value = data.data ?? { ...row }
  visible.value = true
  await Promise.all([loadTimeline(), loadSegments()])
}

async function loadTimeline() {
  if (!wo.value?.id) {
    timelineData.value = null
    return
  }
  const { data } = await http.get(`/repair/workorder/${wo.value.id}/timeline`)
  timelineData.value = data.data ?? null
}

async function refresh() {
  if (wo.value?.id) await openDetail(wo.value)
  crudRef.value?.load()
}

async function saveDraft() {
  if (!wo.value) return
  if (!wo.value.device_id) {
    ElMessage.warning('请选择报修设备')
    return
  }
  if (!wo.value.fault_description) {
    ElMessage.warning('请填写故障描述')
    return
  }
  const photos = wo.value.fault_photos
  if (Array.isArray(photos) && photos.length > 3) {
    ElMessage.warning('故障图片最多 3 张')
    return
  }
  const id = wo.value.id
  const { data } = id
    ? await http.put(`/repair/workorder/${id}`, wo.value)
    : await http.post('/repair/workorder', wo.value)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '保存失败')
    return
  }
  wo.value = data.data
  ElMessage.success(id ? '草稿已保存' : '草稿已创建')
  await loadTimeline()
  crudRef.value?.load()
  if (pageMode.value === 'apply' && wo.value?.status === 'draft') {
    try {
      await ElMessageBox.confirm('草稿已保存，是否立即提交报修？', '是否提交', {
        confirmButtonText: '是',
        cancelButtonText: '否',
        type: 'info',
        distinguishCancelAndClose: true
      })
      await doSubmit(wo.value, { skipConfirm: true })
    } catch {
      /* 否 / 关闭：留草稿 */
    }
  }
}

async function doSubmit(row?: Record<string, unknown>, opts?: { skipConfirm?: boolean }) {
  const target = row ?? wo.value
  if (!target) return
  if (!target.id) {
    wo.value = { ...target }
    await saveDraft()
    return
  }
  if (!opts?.skipConfirm) {
    await ElMessageBox.confirm('提交后将进入维修流程，提交前请确认信息无误。', '提交报修', { type: 'warning' })
  }
  const { data } = await http.post(`/repair/workorder/${target.id}/submit`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '提交失败')
    return
  }
  ElMessage.success('已提交')
  if (!row || opts?.skipConfirm) visible.value = false
  crudRef.value?.load()
}

async function doWithdraw(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target?.id) return
  await ElMessageBox.confirm('撤回后将回到草稿，可再次修改并提交。设备将恢复可用。', '撤回报修', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${target.id}/withdraw`, { remark: '用户撤回' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '撤回失败')
    return
  }
  ElMessage.success('已撤回为草稿')
  if (row) {
    crudRef.value?.load()
    return
  }
  await refresh()
}

async function doDelete(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target?.id) return
  await ElMessageBox.confirm('确认删除该草稿报修单？', '删除', { type: 'warning' })
  const { data } = await http.delete(`/repair/workorder/${target.id}`)
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '删除失败')
    return
  }
  ElMessage.success('已删除')
  if (!row) visible.value = false
  crudRef.value?.load()
}

function openChangeLog(row?: Record<string, unknown>) {
  const id = row?.id ?? wo.value?.id
  if (!id) return
  changeLogEntityId.value = String(id)
  changeLogVisible.value = true
}

function resetActionForm() {
  actionForm.userId = String(wo.value?.assigned_user_id ?? '')
  actionForm.startRepair = false
  actionForm.keepRepairing = false
  actionForm.skipVerify = false
  actionForm.solution = String(wo.value?.solution_description ?? '维修完成')
  actionForm.remark = ''
  actionForm.subStatus = String(wo.value?.repair_sub_status ?? 'internal')
}

function openDispatch(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  resetActionForm()
  dispatchVisible.value = true
}

function openTransfer(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  resetActionForm()
  actionForm.userId = ''
  transferVisible.value = true
}

function openComplete(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  resetActionForm()
  if (status.value === 'verify_rejected') {
    actionForm.skipVerify = false
  }
  completeVisible.value = true
}

function openSubStatus() {
  resetActionForm()
  subVisible.value = true
}

async function doDispatch() {
  if (!wo.value?.id || !actionForm.userId) {
    ElMessage.warning('请选择工程师')
    return
  }
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/dispatch`, {
    userId: actionForm.userId,
    startRepair: actionForm.startRepair,
    remark: actionForm.remark
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '派工失败')
    return
  }
  dispatchVisible.value = false
  ElMessage.success('派工成功')
  await refresh()
}

async function doStartRepair(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/start-repair`, {
    repair_sub_status: 'internal'
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '操作失败')
    return
  }
  ElMessage.success('已开始维修')
  await refresh()
}

async function doGrab(row?: Record<string, unknown>) {
  if (row?.id) {
    await openDetail(row)
  }
  if (!wo.value?.id) return
  await ElMessageBox.confirm('确认抢单？抢单后您将成为负责人并直接开始维修。', '抢单', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/grab`, { remark: '工程师抢单' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '抢单失败')
    return
  }
  ElMessage.success('抢单成功，已开始维修')
  await refresh()
}

async function doAccept(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/accept`, { startRepair: true })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '接单失败')
    return
  }
  ElMessage.success('接单成功')
  await refresh()
}

async function doTransfer() {
  if (!wo.value?.id || !actionForm.userId) {
    ElMessage.warning('请选择目标工程师')
    return
  }
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/transfer`, {
    userId: actionForm.userId,
    keepRepairing: actionForm.keepRepairing,
    remark: actionForm.remark
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '转派失败')
    return
  }
  transferVisible.value = false
  ElMessage.success('转派成功')
  await refresh()
}

async function doSubStatus() {
  if (!wo.value?.id) return
  const { data } = await http.post(`/repair/workorder/${wo.value.id}/sub-status`, {
    repair_sub_status: actionForm.subStatus,
    remark: actionForm.remark
  })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '更新失败')
    return
  }
  subVisible.value = false
  ElMessage.success('子状态已更新')
  await refresh()
}

async function doComplete() {
  if (!wo.value?.id) {
    ElMessage.warning('工单未加载，请关闭后重试')
    return
  }
  try {
    await http.post(`/repair/workorder/${wo.value.id}/complete`, {
      solution_description: actionForm.solution || '维修完成',
      parts_cost: wo.value.parts_cost ?? 0,
      labor_cost: wo.value.labor_cost ?? 0,
      total_cost: wo.value.total_cost ?? 0,
      skipVerify: actionForm.skipVerify
    })
    completeVisible.value = false
    ElMessage.success(actionForm.skipVerify ? '已结案' : '已提交验收')
    await refresh()
  } catch (e: unknown) {
    const err = e as { message?: string; response?: { data?: { message?: string } } }
    ElMessage.error(err.message || err.response?.data?.message || '完工失败')
  }
}

function openVerify(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  actionForm.verifyResult = 'pass'
  actionForm.verifyComment = '验收通过'
  actionForm.satisfactionRating = 5
  verifyVisible.value = true
}

function openRejectVerify(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  actionForm.verifyResult = 'fail'
  actionForm.verifyComment = ''
  actionForm.satisfactionRating = 5
  verifyVisible.value = true
}

async function submitVerify() {
  if (!wo.value?.id) {
    ElMessage.warning('工单未加载，请关闭后重试')
    return
  }
  const result = actionForm.verifyResult
  if (result === 'fail' && !actionForm.verifyComment.trim()) {
    ElMessage.warning('请填写拒绝验收原因')
    return
  }
  try {
    await http.post(`/repair/workorder/${wo.value.id}/verify`, {
      verifier_id: auth.user?.userId ?? wo.value.reporter_id,
      verify_result: result,
      verify_comment: actionForm.verifyComment || (result === 'pass' ? '验收通过' : '验收不通过'),
      satisfaction_rating: result === 'pass' ? actionForm.satisfactionRating : null
    })
    verifyVisible.value = false
    ElMessage.success(result === 'pass' ? '验收通过' : '已拒绝验收，工单退回返修')
    await refresh()
  } catch (e: unknown) {
    const err = e as { message?: string; response?: { data?: { message?: string } } }
    ElMessage.error(err.message || err.response?.data?.message || '验收失败')
  }
}

async function doVerify(result: 'pass' | 'fail') {
  if (!wo.value?.id) return
  try {
    await http.post(`/repair/workorder/${wo.value.id}/verify`, {
      verifier_id: auth.user?.userId ?? wo.value.reporter_id,
      verify_result: result,
      verify_comment: result === 'pass' ? '验收通过' : '验收不通过，需返修',
      satisfaction_rating: result === 'pass' ? 5 : null
    })
    ElMessage.success(result === 'pass' ? '验收通过' : '已拒绝验收，工单退回返修')
    await refresh()
  } catch (e: unknown) {
    const err = e as { message?: string; response?: { data?: { message?: string } } }
    ElMessage.error(err.message || err.response?.data?.message || '验收失败')
  }
}

async function doSuspend(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  if (!wo.value?.id) return
  const { value } = await ElMessageBox.prompt('请输入挂起原因', '挂起工单', { inputPlaceholder: '原因' })
  await http.post(`/repair/workorder/${wo.value.id}/suspend`, { remark: value })
  ElMessage.success('已挂起')
  await refresh()
}

async function doResume(row?: Record<string, unknown>) {
  if (row) {
    wo.value = { ...row }
  }
  if (!wo.value?.id) return
  await http.post(`/repair/workorder/${wo.value.id}/resume`, { repair_sub_status: 'internal' })
  ElMessage.success('已恢复')
  await refresh()
}

async function doCancel(row?: Record<string, unknown>) {
  const target = row ?? wo.value
  if (!target?.id) return
  await ElMessageBox.confirm('确认取消该工单？设备将恢复可用状态。', '取消工单', { type: 'warning' })
  const { data } = await http.post(`/repair/workorder/${target.id}/cancel`, { remark: '用户取消' })
  if (data.code !== 0 && data.code !== 200) {
    ElMessage.error(data.message || '取消失败')
    return
  }
  ElMessage.success('已取消')
  if (row) {
    crudRef.value?.load()
    return
  }
  await refresh()
}

async function loadMyEngineerFlag() {
  if (pageMode.value !== 'handle' && pageMode.value !== 'all') {
    isRepairEngineer.value = false
    return
  }
  try {
    const { data } = await http.get('/repair/engineer/me')
    isRepairEngineer.value = Boolean(data.data?.isRepairEngineer)
  } catch {
    isRepairEngineer.value = false
  }
}

onMounted(() => {
  void loadMyEngineerFlag()
})
onActivated(() => {
  void loadMyEngineerFlag()
})
</script>

<style scoped>
.timeline-section {
  margin-top: 16px;
}
.timeline-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.segments {
  margin: 8px 0 12px;
  padding-left: 8px;
}
.seg-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.seg-row,
.event-row {
  font-size: 13px;
  line-height: 1.7;
}
.seg-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.seg-row:last-child {
  border-bottom: none;
}
.event-collapse {
  margin-top: 8px;
}
.seg-parts {
  margin-top: 4px;
  padding-left: 8px;
}
.seg-part-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.seg-part-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.seg-end-time {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.seg-engineer-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.seg-engineer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.seg-users {
  margin-top: 4px;
  padding-left: 8px;
}
.event-time {
  display: inline-block;
  min-width: 140px;
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
</style>
