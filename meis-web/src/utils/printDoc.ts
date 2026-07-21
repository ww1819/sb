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

/** 金额转中文大写（财务习惯，精确到分） */
export function amountToChineseYuan(amount: number): string {
  if (!Number.isFinite(amount)) return ''
  const neg = amount < 0
  const n = Math.round(Math.abs(amount) * 100)
  if (n === 0) return '零元整'
  const digits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖']
  const intUnits = ['', '拾', '佰', '仟']
  const secUnits = ['', '万', '亿']
  const yuan = Math.floor(n / 100)
  const jiao = Math.floor((n % 100) / 10)
  const fen = n % 10
  let intStr = ''
  if (yuan > 0) {
    const s = String(yuan)
    const sections: string[] = []
    let secIdx = 0
    for (let end = s.length; end > 0; end -= 4, secIdx++) {
      const start = Math.max(0, end - 4)
      const part = s.slice(start, end)
      let partStr = ''
      let zero = false
      for (let i = 0; i < part.length; i++) {
        const d = Number(part[i])
        const u = intUnits[part.length - 1 - i]
        if (d === 0) {
          zero = true
        } else {
          if (zero) partStr += '零'
          zero = false
          partStr += digits[d] + u
        }
      }
      if (partStr) sections.unshift(partStr + secUnits[secIdx])
      else if (secIdx > 0 && sections.length) {
        // skip empty section
      }
    }
    intStr = sections.join('').replace(/零+/g, '零').replace(/零$/, '') + '元'
  }
  let decStr = ''
  if (jiao === 0 && fen === 0) decStr = '整'
  else {
    if (jiao > 0) decStr += digits[jiao] + '角'
    else if (fen > 0 && yuan > 0) decStr += '零'
    if (fen > 0) decStr += digits[fen] + '分'
  }
  return (neg ? '负' : '') + (intStr || '零元') + decStr
}

function fmtMoney(v: unknown, digits = 2): string {
  if (v == null || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(digits) : String(v)
}

function fmtDate(v: unknown): string {
  if (v == null || v === '') return ''
  const s = String(v)
  return s.length >= 10 ? s.slice(0, 10) : s
}

export interface EntryPrintRow {
  name: string
  spec: string
  batch: string
  qty: string
  price: string
  amount: string
}

export interface EntryPrintModel {
  hospital: string
  warehouse: string
  docNo: string
  approvedAt: string
  operator: string
  approver: string
  printDate: string
  chineseTotal: string
  qtySum: string
  amountSum: string
  rows: EntryPrintRow[]
}

/** 组装设备入库单打印数据（供弹窗预览 / 打印） */
export function buildEntryPrintModel(
  entry: Record<string, unknown>,
  hospitalName?: string
): EntryPrintModel {
  const hospital = hospitalName || DEFAULT_HOSPITAL
  const items = (entry.items as Record<string, unknown>[]) ?? []
  const today = new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')
  let qtySum = 0
  let amtSum = 0
  const rows: EntryPrintRow[] = items.map((i) => {
    const qty = Number(i.quantity ?? 0)
    const price = Number(i.unit_price ?? 0)
    let amt = Number(i.total_price)
    if (!Number.isFinite(amt)) amt = Number.isFinite(qty) && Number.isFinite(price) ? qty * price : 0
    if (Number.isFinite(qty)) qtySum += qty
    if (Number.isFinite(amt)) amtSum += amt
    return {
      name: String(i.device_name ?? ''),
      spec: String(i.specification ?? i.model ?? ''),
      batch: String(i.serial_number ?? ''),
      qty: Number.isFinite(qty) ? String(qty) : '',
      price: fmtMoney(price, 2),
      amount: fmtMoney(amt, 2)
    }
  })
  if (!rows.length && entry.total_amount != null) {
    amtSum = Number(entry.total_amount) || 0
  }
  return {
    hospital,
    warehouse: String(entry.warehouse_name ?? ''),
    docNo: String(entry.entry_no ?? ''),
    approvedAt: fmtDate(entry.approved_at ?? entry.entry_date),
    operator: String(entry.created_by_name ?? entry.operator_name ?? ''),
    approver: String(entry.approved_by_name ?? ''),
    printDate: today,
    chineseTotal: amountToChineseYuan(Math.round(amtSum * 100) / 100),
    qtySum: qtySum ? String(qtySum) : '',
    amountSum: fmtMoney(amtSum, 2),
    rows
  }
}

/** 当前页内 iframe 打印，不新开浏览器标签 */
export function printHtmlInPage(html: string) {
  const iframe = document.createElement('iframe')
  iframe.setAttribute('aria-hidden', 'true')
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;opacity:0;pointer-events:none'
  document.body.appendChild(iframe)
  const doc = iframe.contentDocument || iframe.contentWindow?.document
  if (!doc) {
    document.body.removeChild(iframe)
    return false
  }
  doc.open()
  doc.write(html)
  doc.close()
  const win = iframe.contentWindow
  if (!win) {
    document.body.removeChild(iframe)
    return false
  }
  const cleanup = () => {
    setTimeout(() => {
      if (iframe.parentNode) iframe.parentNode.removeChild(iframe)
    }, 800)
  }
  win.focus()
  setTimeout(() => {
    try {
      win.print()
    } finally {
      cleanup()
    }
  }, 80)
  return true
}

/**
 * 设备入库单打印（版式对齐科室退库单：标题 / 元信息 / 明细表 / 合计 / 签栏）
 * @deprecated 列表请用弹窗预览；保留供需要直接出纸的场景
 */
export function printEntryDoc(entry: Record<string, unknown>, hospitalName?: string) {
  const model = buildEntryPrintModel(entry, hospitalName)
  return printHtmlInPage(renderEntryPrintHtml(model))
}

export function renderEntryPrintHtml(model: EntryPrintModel) {
  const rowsHtml = model.rows
    .map(
      (r) => `<tr>
      <td class="left">${esc(r.name)}</td>
      <td class="left">${esc(r.spec)}</td>
      <td>${esc(r.batch)}</td>
      <td class="num">${esc(r.qty)}</td>
      <td class="num">${esc(r.price)}</td>
      <td class="num">${esc(r.amount)}</td>
    </tr>`
    )
    .join('')

  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备入库单</title>
<style>
@page{size:A4;margin:14mm 12mm}
*{box-sizing:border-box}
body{font-family:"SimSun","宋体",serif;font-size:13px;color:#000;margin:0;padding:0}
.sheet{width:100%}
.title{text-align:center;font-size:22px;font-weight:bold;letter-spacing:3px;margin:0 0 14px}
.meta-row{display:flex;justify-content:space-between;gap:12px;margin:4px 0 10px;font-size:13px}
.meta-row .cell{flex:1}
.meta-row .cell.center{text-align:center}
.meta-row .cell.right{text-align:right}
table.grid{width:100%;border-collapse:collapse;table-layout:fixed}
table.grid th,table.grid td{border:1px solid #000;padding:6px 5px;vertical-align:middle}
table.grid th{font-weight:bold;text-align:center;background:#fff}
table.grid td{text-align:center}
table.grid td.left{text-align:left}
table.grid td.num{text-align:right}
table.grid .total-label{text-align:left;font-weight:bold}
.footer{display:flex;justify-content:space-between;margin-top:28px;font-size:13px;padding:0 4px}
.footer .cell{flex:1}
.footer .cell.center{text-align:center}
.footer .cell.right{text-align:right}
</style></head><body>
<div class="sheet">
  <div class="title">${esc(model.hospital)}设备入库单</div>
  <div class="meta-row">
    <div class="cell">仓库：${esc(model.warehouse || '-')}</div>
    <div class="cell center"></div>
    <div class="cell right">单据号：${esc(model.docNo || '-')}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${esc(model.approvedAt || '-')}</div>
    <div class="cell"></div>
    <div class="cell"></div>
  </div>
  <table class="grid">
    <thead>
      <tr>
        <th style="width:22%">设备名称</th>
        <th style="width:18%">规格型号</th>
        <th style="width:16%">批次(序列号)</th>
        <th style="width:10%">数量</th>
        <th style="width:16%">单价</th>
        <th style="width:18%">金额</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml || `<tr><td colspan="6" style="height:36px"></td></tr>`}
      <tr>
        <td class="total-label" colspan="3">合计：${esc(model.chineseTotal)}</td>
        <td class="num">${esc(model.qtySum)}</td>
        <td></td>
        <td class="num">${esc(model.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">入库经办人：${esc(model.operator)}</div>
    <div class="cell center">审核人：${esc(model.approver)}</div>
    <div class="cell right">打印日期：${esc(model.printDate)}</div>
  </div>
</div>
</body></html>`
}

/** 设备退货单打印（版式对齐设备入库单） */
export function printGoodsReturnDoc(doc: Record<string, unknown>, hospitalName?: string) {
  const model = buildGoodsReturnPrintModel(doc, hospitalName)
  return printHtmlInPage(renderGoodsReturnPrintHtml(model))
}

export interface GoodsReturnPrintModel {
  hospital: string
  warehouse: string
  supplier: string
  docNo: string
  reason: string
  approvedAt: string
  operator: string
  approver: string
  printDate: string
  chineseTotal: string
  qtySum: string
  amountSum: string
  rows: EntryPrintRow[]
}

export function buildGoodsReturnPrintModel(
  doc: Record<string, unknown>,
  hospitalName?: string
): GoodsReturnPrintModel {
  const hospital = hospitalName || DEFAULT_HOSPITAL
  const items = (doc.items as Record<string, unknown>[]) ?? []
  const today = new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')
  let qtySum = 0
  let amtSum = 0
  const rows: EntryPrintRow[] = items.map((i) => {
    const qty = Number(i.quantity ?? 0)
    const price = Number(i.unit_price ?? 0)
    let amt = Number(i.total_price)
    if (!Number.isFinite(amt)) amt = Number.isFinite(qty) && Number.isFinite(price) ? qty * price : 0
    if (Number.isFinite(qty)) qtySum += qty
    if (Number.isFinite(amt)) amtSum += amt
    return {
      name: String(i.device_name ?? ''),
      spec: String(i.specification ?? i.model ?? ''),
      batch: String(i.serial_number ?? ''),
      qty: Number.isFinite(qty) ? String(qty) : '',
      price: fmtMoney(price, 2),
      amount: fmtMoney(amt, 2)
    }
  })
  if (!rows.length && doc.total_amount != null) {
    amtSum = Number(doc.total_amount) || 0
  }
  return {
    hospital,
    warehouse: String(doc.warehouse_name ?? ''),
    supplier: String(doc.supplier_name ?? ''),
    docNo: String(doc.return_no ?? ''),
    reason: String(doc.reason ?? ''),
    approvedAt: fmtDate(doc.approved_at ?? doc.return_date),
    operator: String(doc.created_by_name ?? ''),
    approver: String(doc.approved_by_name ?? ''),
    printDate: today,
    chineseTotal: amountToChineseYuan(Math.round(amtSum * 100) / 100),
    qtySum: qtySum ? String(qtySum) : '',
    amountSum: fmtMoney(amtSum, 2),
    rows
  }
}

export function renderGoodsReturnPrintHtml(model: GoodsReturnPrintModel) {
  const rowsHtml = model.rows
    .map(
      (r) => `<tr>
      <td class="left">${esc(r.name)}</td>
      <td class="left">${esc(r.spec)}</td>
      <td>${esc(r.batch)}</td>
      <td class="num">${esc(r.qty)}</td>
      <td class="num">${esc(r.price)}</td>
      <td class="num">${esc(r.amount)}</td>
    </tr>`
    )
    .join('')

  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备退货单</title>
<style>
@page{size:A4;margin:14mm 12mm}
*{box-sizing:border-box}
body{font-family:"SimSun","宋体",serif;font-size:13px;color:#000;margin:0;padding:0}
.sheet{width:100%}
.title{text-align:center;font-size:22px;font-weight:bold;letter-spacing:3px;margin:0 0 14px}
.meta-row{display:flex;justify-content:space-between;gap:12px;margin:4px 0 10px;font-size:13px}
.meta-row .cell{flex:1}
.meta-row .cell.center{text-align:center}
.meta-row .cell.right{text-align:right}
table.grid{width:100%;border-collapse:collapse;table-layout:fixed}
table.grid th,table.grid td{border:1px solid #000;padding:6px 5px;vertical-align:middle}
table.grid th{font-weight:bold;text-align:center;background:#fff}
table.grid td{text-align:center}
table.grid td.left{text-align:left}
table.grid td.num{text-align:right}
table.grid .total-label{text-align:left;font-weight:bold}
.footer{display:flex;justify-content:space-between;margin-top:28px;font-size:13px;padding:0 4px}
.footer .cell{flex:1}
.footer .cell.center{text-align:center}
.footer .cell.right{text-align:right}
</style></head><body>
<div class="sheet">
  <div class="title">${esc(model.hospital)}设备退货单</div>
  <div class="meta-row">
    <div class="cell">仓库：${esc(model.warehouse || '-')}</div>
    <div class="cell center">供应商：${esc(model.supplier || '-')}</div>
    <div class="cell right">单据号：${esc(model.docNo || '-')}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${esc(model.approvedAt || '-')}</div>
    <div class="cell">退货原因：${esc(model.reason || '-')}</div>
    <div class="cell"></div>
  </div>
  <table class="grid">
    <thead>
      <tr>
        <th style="width:22%">资产名称</th>
        <th style="width:18%">规格型号</th>
        <th style="width:16%">序列号(SN)</th>
        <th style="width:10%">数量</th>
        <th style="width:16%">单价</th>
        <th style="width:18%">金额</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml || `<tr><td colspan="6" style="height:36px"></td></tr>`}
      <tr>
        <td class="total-label" colspan="3">合计：${esc(model.chineseTotal)}</td>
        <td class="num">${esc(model.qtySum)}</td>
        <td></td>
        <td class="num">${esc(model.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">制单人：${esc(model.operator)}</div>
    <div class="cell center">审核人：${esc(model.approver)}</div>
    <div class="cell right">打印日期：${esc(model.printDate)}</div>
  </div>
</div>
</body></html>`
}

/** 设备出库单打印（版式对齐设备退货单） */
export function printOutboundDoc(doc: Record<string, unknown>, hospitalName?: string) {
  const model = buildOutboundPrintModel(doc, hospitalName)
  return printHtmlInPage(renderOutboundPrintHtml(model))
}

export interface OutboundPrintModel {
  hospital: string
  warehouse: string
  dept: string
  docNo: string
  receiver: string
  purpose: string
  approvedAt: string
  operator: string
  approver: string
  printDate: string
  chineseTotal: string
  qtySum: string
  amountSum: string
  rows: EntryPrintRow[]
}

export function buildOutboundPrintModel(
  doc: Record<string, unknown>,
  hospitalName?: string
): OutboundPrintModel {
  const hospital = hospitalName || DEFAULT_HOSPITAL
  const items = (doc.items as Record<string, unknown>[]) ?? []
  const today = new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')
  let qtySum = 0
  let amtSum = 0
  const rows: EntryPrintRow[] = items.map((i) => {
    const qty = Number(i.quantity ?? 0)
    const price = Number(i.unit_price ?? 0)
    let amt = Number(i.total_price)
    if (!Number.isFinite(amt)) amt = Number.isFinite(qty) && Number.isFinite(price) ? qty * price : 0
    if (Number.isFinite(qty)) qtySum += qty
    if (Number.isFinite(amt)) amtSum += amt
    return {
      name: String(i.device_name ?? ''),
      spec: String(i.specification ?? i.model ?? ''),
      batch: String(i.serial_number ?? ''),
      qty: Number.isFinite(qty) ? String(qty) : '',
      price: fmtMoney(price, 2),
      amount: fmtMoney(amt, 2)
    }
  })
  if (!rows.length && doc.total_amount != null) {
    amtSum = Number(doc.total_amount) || 0
  }
  return {
    hospital,
    warehouse: String(doc.warehouse_name ?? ''),
    dept: String(doc.dept_name ?? ''),
    docNo: String(doc.outbound_no ?? ''),
    receiver: String(doc.receiver_name ?? ''),
    purpose: String(doc.purpose ?? ''),
    approvedAt: fmtDate(doc.approved_at ?? doc.outbound_date),
    operator: String(doc.created_by_name ?? ''),
    approver: String(doc.approved_by_name ?? ''),
    printDate: today,
    chineseTotal: amountToChineseYuan(Math.round(amtSum * 100) / 100),
    qtySum: qtySum ? String(qtySum) : '',
    amountSum: fmtMoney(amtSum, 2),
    rows
  }
}

export function renderOutboundPrintHtml(model: OutboundPrintModel) {
  const rowsHtml = model.rows
    .map(
      (r) => `<tr>
      <td class="left">${esc(r.name)}</td>
      <td class="left">${esc(r.spec)}</td>
      <td>${esc(r.batch)}</td>
      <td class="num">${esc(r.qty)}</td>
      <td class="num">${esc(r.price)}</td>
      <td class="num">${esc(r.amount)}</td>
    </tr>`
    )
    .join('')

  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备出库单</title>
<style>
@page{size:A4;margin:14mm 12mm}
*{box-sizing:border-box}
body{font-family:"SimSun","宋体",serif;font-size:13px;color:#000;margin:0;padding:0}
.sheet{width:100%}
.title{text-align:center;font-size:22px;font-weight:bold;letter-spacing:3px;margin:0 0 14px}
.meta-row{display:flex;justify-content:space-between;gap:12px;margin:4px 0 10px;font-size:13px}
.meta-row .cell{flex:1}
.meta-row .cell.center{text-align:center}
.meta-row .cell.right{text-align:right}
table.grid{width:100%;border-collapse:collapse;table-layout:fixed}
table.grid th,table.grid td{border:1px solid #000;padding:6px 5px;vertical-align:middle}
table.grid th{font-weight:bold;text-align:center;background:#fff}
table.grid td{text-align:center}
table.grid td.left{text-align:left}
table.grid td.num{text-align:right}
table.grid .total-label{text-align:left;font-weight:bold}
.footer{display:flex;justify-content:space-between;margin-top:28px;font-size:13px;padding:0 4px}
.footer .cell{flex:1}
.footer .cell.center{text-align:center}
.footer .cell.right{text-align:right}
</style></head><body>
<div class="sheet">
  <div class="title">${esc(model.hospital)}设备出库单</div>
  <div class="meta-row">
    <div class="cell">仓库：${esc(model.warehouse || '-')}</div>
    <div class="cell center">科室：${esc(model.dept || '-')}</div>
    <div class="cell right">单据号：${esc(model.docNo || '-')}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${esc(model.approvedAt || '-')}</div>
    <div class="cell">领用人：${esc(model.receiver || '-')}</div>
    <div class="cell right">用途：${esc(model.purpose || '-')}</div>
  </div>
  <table class="grid">
    <thead>
      <tr>
        <th style="width:22%">资产名称</th>
        <th style="width:18%">规格型号</th>
        <th style="width:16%">序列号(SN)</th>
        <th style="width:10%">数量</th>
        <th style="width:16%">单价</th>
        <th style="width:18%">金额</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml || `<tr><td colspan="6" style="height:36px"></td></tr>`}
      <tr>
        <td class="total-label" colspan="3">合计：${esc(model.chineseTotal)}</td>
        <td class="num">${esc(model.qtySum)}</td>
        <td></td>
        <td class="num">${esc(model.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">制单人：${esc(model.operator)}</div>
    <div class="cell center">审核人：${esc(model.approver)}</div>
    <div class="cell right">打印日期：${esc(model.printDate)}</div>
  </div>
</div>
</body></html>`
}

/** 设备退库单打印（版式对齐设备退货单） */
export function printDeviceReturnDoc(doc: Record<string, unknown>, hospitalName?: string) {
  const model = buildDeviceReturnPrintModel(doc, hospitalName)
  return printHtmlInPage(renderDeviceReturnPrintHtml(model))
}

export interface DeviceReturnPrintModel {
  hospital: string
  warehouse: string
  dept: string
  docNo: string
  reason: string
  approvedAt: string
  operator: string
  approver: string
  printDate: string
  chineseTotal: string
  qtySum: string
  amountSum: string
  rows: EntryPrintRow[]
}

export function buildDeviceReturnPrintModel(
  doc: Record<string, unknown>,
  hospitalName?: string
): DeviceReturnPrintModel {
  const hospital = hospitalName || DEFAULT_HOSPITAL
  const items = (doc.items as Record<string, unknown>[]) ?? []
  const today = new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')
  let qtySum = 0
  let amtSum = 0
  const rows: EntryPrintRow[] = items.map((i) => {
    const qty = Number(i.quantity ?? 0)
    const price = Number(i.unit_price ?? 0)
    let amt = Number(i.total_price)
    if (!Number.isFinite(amt)) amt = Number.isFinite(qty) && Number.isFinite(price) ? qty * price : 0
    if (Number.isFinite(qty)) qtySum += qty
    if (Number.isFinite(amt)) amtSum += amt
    return {
      name: String(i.device_name ?? ''),
      spec: String(i.specification ?? i.model ?? ''),
      batch: String(i.serial_number ?? ''),
      qty: Number.isFinite(qty) ? String(qty) : '',
      price: fmtMoney(price, 2),
      amount: fmtMoney(amt, 2)
    }
  })
  if (!rows.length && doc.total_amount != null) {
    amtSum = Number(doc.total_amount) || 0
  }
  return {
    hospital,
    warehouse: String(doc.warehouse_name ?? ''),
    dept: String(doc.dept_name ?? ''),
    docNo: String(doc.return_no ?? ''),
    reason: String(doc.reason ?? ''),
    approvedAt: fmtDate(doc.approved_at ?? doc.return_date),
    operator: String(doc.created_by_name ?? ''),
    approver: String(doc.approved_by_name ?? ''),
    printDate: today,
    chineseTotal: amountToChineseYuan(Math.round(amtSum * 100) / 100),
    qtySum: qtySum ? String(qtySum) : '',
    amountSum: fmtMoney(amtSum, 2),
    rows
  }
}

export function renderDeviceReturnPrintHtml(model: DeviceReturnPrintModel) {
  const rowsHtml = model.rows
    .map(
      (r) => `<tr>
      <td class="left">${esc(r.name)}</td>
      <td class="left">${esc(r.spec)}</td>
      <td>${esc(r.batch)}</td>
      <td class="num">${esc(r.qty)}</td>
      <td class="num">${esc(r.price)}</td>
      <td class="num">${esc(r.amount)}</td>
    </tr>`
    )
    .join('')

  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备退库单</title>
<style>
@page{size:A4;margin:14mm 12mm}
*{box-sizing:border-box}
body{font-family:"SimSun","宋体",serif;font-size:13px;color:#000;margin:0;padding:0}
.sheet{width:100%}
.title{text-align:center;font-size:22px;font-weight:bold;letter-spacing:3px;margin:0 0 14px}
.meta-row{display:flex;justify-content:space-between;gap:12px;margin:4px 0 10px;font-size:13px}
.meta-row .cell{flex:1}
.meta-row .cell.center{text-align:center}
.meta-row .cell.right{text-align:right}
table.grid{width:100%;border-collapse:collapse;table-layout:fixed}
table.grid th,table.grid td{border:1px solid #000;padding:6px 5px;vertical-align:middle}
table.grid th{font-weight:bold;text-align:center;background:#fff}
table.grid td{text-align:center}
table.grid td.left{text-align:left}
table.grid td.num{text-align:right}
table.grid .total-label{text-align:left;font-weight:bold}
.footer{display:flex;justify-content:space-between;margin-top:28px;font-size:13px;padding:0 4px}
.footer .cell{flex:1}
.footer .cell.center{text-align:center}
.footer .cell.right{text-align:right}
</style></head><body>
<div class="sheet">
  <div class="title">${esc(model.hospital)}设备退库单</div>
  <div class="meta-row">
    <div class="cell">仓库：${esc(model.warehouse || '-')}</div>
    <div class="cell center">科室：${esc(model.dept || '-')}</div>
    <div class="cell right">单据号：${esc(model.docNo || '-')}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${esc(model.approvedAt || '-')}</div>
    <div class="cell">退库原因：${esc(model.reason || '-')}</div>
    <div class="cell"></div>
  </div>
  <table class="grid">
    <thead>
      <tr>
        <th style="width:22%">资产名称</th>
        <th style="width:18%">规格型号</th>
        <th style="width:16%">序列号(SN)</th>
        <th style="width:10%">数量</th>
        <th style="width:16%">单价</th>
        <th style="width:18%">金额</th>
      </tr>
    </thead>
    <tbody>
      ${rowsHtml || `<tr><td colspan="6" style="height:36px"></td></tr>`}
      <tr>
        <td class="total-label" colspan="3">合计：${esc(model.chineseTotal)}</td>
        <td class="num">${esc(model.qtySum)}</td>
        <td></td>
        <td class="num">${esc(model.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">制单人：${esc(model.operator)}</div>
    <div class="cell center">审核人：${esc(model.approver)}</div>
    <div class="cell right">打印日期：${esc(model.printDate)}</div>
  </div>
</div>
</body></html>`
}
