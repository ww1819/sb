export interface PrintTableSection {
  title?: string
  headers: string[]
  rows: string[][]
}

export interface HospitalPrintOptions {
  title: string
  docNo?: string
  subtitle?: string
  hospitalName?: string
  fields: [string, string][]
  tables?: PrintTableSection[]
  signatures?: string[]
  footerNote?: string
}

const DEFAULT_HOSPITAL = '医疗机构（采购管理）'

function esc(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderTable(section: PrintTableSection) {
  const head = section.headers.map(h => `<th>${esc(h)}</th>`).join('')
  const body = section.rows.map(row =>
    `<tr>${row.map(c => `<td>${esc(c ?? '')}</td>`).join('')}</tr>`
  ).join('')
  const title = section.title ? `<h3 class="sub">${esc(section.title)}</h3>` : ''
  return `${title}<table class="data"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`
}

export function printHospitalDoc(opts: HospitalPrintOptions) {
  const hospital = opts.hospitalName || DEFAULT_HOSPITAL
  const today = new Date().toLocaleDateString('zh-CN')
  const sigHtml = (opts.signatures?.length
    ? `<div class="signatures">${opts.signatures.map(s => `<div class="sig-cell"><div class="sig-line"></div><div>${esc(s)}</div></div>`).join('')}</div>`
    : '')
  const tables = (opts.tables ?? []).map(renderTable).join('')
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${esc(opts.title)}</title>
<style>
@page{margin:18mm}body{font-family:SimSun,serif;font-size:14px;color:#000;padding:0}
.header{text-align:center;border-bottom:2px solid #000;padding-bottom:12px;margin-bottom:16px}
.hospital{font-size:18px;font-weight:bold;letter-spacing:2px}
.title{font-size:20px;font-weight:bold;margin-top:8px}
.subtitle{font-size:13px;color:#444;margin-top:4px}
.meta{display:flex;justify-content:space-between;margin:12px 0 16px;font-size:13px}
table{width:100%;border-collapse:collapse;margin:12px 0}
table.info td,table.info th{border:1px solid #333;padding:8px 10px}
table.info .label{width:130px;background:#f5f5f5;font-weight:bold;text-align:center}
table.data th,table.data td{border:1px solid #333;padding:6px 8px;font-size:13px}
table.data th{background:#eee}
.sub{font-size:15px;margin:16px 0 8px;font-weight:bold}
.signatures{display:flex;justify-content:space-around;margin-top:48px}
.sig-cell{text-align:center;width:22%}
.sig-line{border-bottom:1px solid #333;height:40px;margin-bottom:6px}
.footer{margin-top:32px;font-size:12px;color:#666;text-align:center}
</style></head><body>
<div class="header">
  <div class="hospital">${esc(hospital)}</div>
  <div class="title">${esc(opts.title)}</div>
  ${opts.subtitle ? `<div class="subtitle">${esc(opts.subtitle)}</div>` : ''}
</div>
<div class="meta">
  <span>单据编号：${esc(opts.docNo ?? '-')}</span>
  <span>打印日期：${today}</span>
</div>
<table class="info">${opts.fields.map(([k, v]) => `<tr><td class="label">${esc(k)}</td><td>${esc(v ?? '')}</td></tr>`).join('')}</table>
${tables}
${sigHtml}
<div class="footer">${esc(opts.footerNote ?? '本单据由 MEIS 采购管理系统自动生成')}</div>
<script>window.onload=function(){window.print()}<\/script></body></html>`
  const w = window.open('', '_blank')
  if (!w) return
  w.document.write(html)
  w.document.close()
}

/** @deprecated use printHospitalDoc */
export function printPurchaseDoc(title: string, rows: [string, string][]) {
  printHospitalDoc({ title, fields: rows })
}

export function printPlanDoc(plan: Record<string, unknown>) {
  const items = (plan.items as Record<string, unknown>[]) ?? []
  printHospitalDoc({
    title: '医疗设备采购计划申报表',
    docNo: String(plan.plan_code ?? ''),
    subtitle: `${plan.plan_year ?? ''} 年度采购计划`,
    fields: [
      ['业务链编号', String(plan.business_chain_no ?? '')],
      ['计划年度', String(plan.plan_year ?? '')],
      ['计划类型', String(plan.plan_type ?? '')],
      ['资金来源', String(plan.fund_source ?? '')],
      ['预算总额', String(plan.total_budget ?? '')],
      ['大型设备', plan.is_large_equipment ? '是' : '否'],
      ['审批状态', String(plan.approval_status ?? '')],
      ['论证说明', String(plan.justification ?? '')]
    ],
    tables: items.length ? [{
      title: '计划明细',
      headers: ['设备名称', '数量', '单位', '预估单价', '金额小计'],
      rows: items.map(i => [
        String(i.device_name ?? ''),
        String(i.quantity ?? ''),
        String(i.unit ?? ''),
        String(i.estimated_price ?? ''),
        String(i.total_price ?? '')
      ])
    }] : undefined,
    signatures: ['申报科室负责人', '医学装备科', '分管院领导']
  })
}

export function printProjectDoc(project: Record<string, unknown>) {
  const bidders = (project.bidders as Record<string, unknown>[]) ?? []
  printHospitalDoc({
    title: '采购项目审批单',
    docNo: String(project.project_code ?? ''),
    fields: [
      ['项目名称', String(project.project_name ?? '')],
      ['采购方式', String(project.purchase_method ?? '')],
      ['招标代理', String(project.bid_agency ?? '')],
      ['控制价', String(project.control_price ?? '')],
      ['项目金额', String(project.total_amount ?? '')],
      ['项目状态', String(project.status ?? '')],
      ['审批状态', String(project.approval_status ?? '')]
    ],
    tables: bidders.length ? [{
      title: '投标人一览',
      headers: ['投标人', '报价', '联系人', '是否中标'],
      rows: bidders.map(b => [
        String(b.bidder_name ?? ''),
        String(b.bid_amount ?? ''),
        String(b.contact_person ?? ''),
        b.is_winner ? '是' : '否'
      ])
    }] : undefined,
    signatures: ['采购经办人', '招标负责人', '装备部负责人']
  })
}

export function printContractDoc(contract: Record<string, unknown>) {
  const payments = (contract.payments as Record<string, unknown>[]) ?? []
  printHospitalDoc({
    title: '医疗设备采购合同审批单',
    docNo: String(contract.contract_code ?? ''),
    fields: [
      ['合同名称', String(contract.contract_name ?? '')],
      ['合同类型', String(contract.contract_type ?? '')],
      ['合同金额', String(contract.contract_amount ?? '')],
      ['签订日期', String(contract.sign_date ?? '')],
      ['交货期限', String(contract.delivery_deadline ?? '')],
      ['付款进度', String(contract.payment_progress ?? '') + '%'],
      ['审批状态', String(contract.approval_status ?? '')]
    ],
    tables: payments.length ? [{
      title: '付款计划',
      headers: ['付款单号', '阶段', '金额', '状态'],
      rows: payments.map(p => [
        String(p.payment_no ?? ''),
        String(p.payment_stage ?? ''),
        String(p.payment_amount ?? ''),
        String(p.status ?? '')
      ])
    }] : undefined,
    signatures: ['合同经办人', '财务审核', '分管院领导']
  })
}

export function printAcceptanceDoc(acc: Record<string, unknown>) {
  const items = (acc.items as Record<string, unknown>[]) ?? []
  const members = (acc.members as Record<string, unknown>[]) ?? []
  const passed = items.filter(i => i.is_passed).length
  printHospitalDoc({
    title: '医疗设备安装验收单',
    docNo: String(acc.acceptance_no ?? ''),
    fields: [
      ['验收日期', String(acc.acceptance_date ?? '')],
      ['质检通过', acc.quality_check_passed ? '是' : '否'],
      ['安装完成', acc.installation_completed ? '是' : '否'],
      ['清单通过', `${passed}/${items.length}`],
      ['论证摘要', String(acc.argument_summary ?? '')]
    ],
    tables: [
      ...(items.length ? [{
        title: '验收清单',
        headers: ['检查项目', '验收标准', '结果', '通过'],
        rows: items.map(i => [
          String(i.item_name ?? ''),
          String(i.check_standard ?? ''),
          String(i.check_result ?? ''),
          i.is_passed ? '是' : '否'
        ])
      }] : []),
      ...(members.length ? [{
        title: '验收小组',
        headers: ['角色', '姓名', '备注'],
        rows: members.map(m => [
          String(m.member_role ?? ''),
          String(m.member_name ?? ''),
          String(m.remark ?? '')
        ])
      }] : [])
    ],
    signatures: ['质控签字', '工程签字', '临床签字', '设备科签字']
  })
}
