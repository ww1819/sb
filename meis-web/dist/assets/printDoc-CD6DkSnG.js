const c="医疗机构（采购管理）";function a(t){return t.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}function m(t){const i=t.headers.map(n=>`<th>${a(n)}</th>`).join(""),e=t.rows.map(n=>`<tr>${n.map(o=>`<td>${a(o??"")}</td>`).join("")}</tr>`).join("");return`${t.title?`<h3 class="sub">${a(t.title)}</h3>`:""}<table class="data"><thead><tr>${i}</tr></thead><tbody>${e}</tbody></table>`}function r(t){var g;const i=t.hospitalName||c,e=new Date().toLocaleDateString("zh-CN"),s=(g=t.signatures)!=null&&g.length?`<div class="signatures">${t.signatures.map(d=>`<div class="sig-cell"><div class="sig-line"></div><div>${a(d)}</div></div>`).join("")}</div>`:"",n=(t.tables??[]).map(m).join(""),o=`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${a(t.title)}</title>
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
  <div class="hospital">${a(i)}</div>
  <div class="title">${a(t.title)}</div>
  ${t.subtitle?`<div class="subtitle">${a(t.subtitle)}</div>`:""}
</div>
<div class="meta">
  <span>单据编号：${a(t.docNo??"-")}</span>
  <span>打印日期：${e}</span>
</div>
<table class="info">${t.fields.map(([d,p])=>`<tr><td class="label">${a(d)}</td><td>${a(p??"")}</td></tr>`).join("")}</table>
${n}
${s}
<div class="footer">${a(t.footerNote??"本单据由 MEIS 采购管理系统自动生成")}</div>
<script>window.onload=function(){window.print()}<\/script></body></html>`,l=window.open("","_blank");l&&(l.document.write(o),l.document.close())}function b(t){const i=t.items??[];r({title:"医疗设备采购计划申报表",docNo:String(t.plan_code??""),subtitle:`${t.plan_year??""} 年度采购计划`,fields:[["业务链编号",String(t.business_chain_no??"")],["计划年度",String(t.plan_year??"")],["计划类型",String(t.plan_type??"")],["资金来源",String(t.fund_source??"")],["预算总额",String(t.total_budget??"")],["大型设备",t.is_large_equipment?"是":"否"],["审批状态",String(t.approval_status??"")],["论证说明",String(t.justification??"")]],tables:i.length?[{title:"计划明细",headers:["设备名称","数量","单位","预估单价","金额小计"],rows:i.map(e=>[String(e.device_name??""),String(e.quantity??""),String(e.unit??""),String(e.estimated_price??""),String(e.total_price??"")])}]:void 0,signatures:["申报科室负责人","医学装备科","分管院领导"]})}function u(t){const i=t.bidders??[];r({title:"采购项目审批单",docNo:String(t.project_code??""),fields:[["项目名称",String(t.project_name??"")],["采购方式",String(t.purchase_method??"")],["招标代理",String(t.bid_agency??"")],["控制价",String(t.control_price??"")],["项目金额",String(t.total_amount??"")],["项目状态",String(t.status??"")],["审批状态",String(t.approval_status??"")]],tables:i.length?[{title:"投标人一览",headers:["投标人","报价","联系人","是否中标"],rows:i.map(e=>[String(e.bidder_name??""),String(e.bid_amount??""),String(e.contact_person??""),e.is_winner?"是":"否"])}]:void 0,signatures:["采购经办人","招标负责人","装备部负责人"]})}function h(t){const i=t.payments??[];r({title:"医疗设备采购合同审批单",docNo:String(t.contract_code??""),fields:[["合同名称",String(t.contract_name??"")],["合同类型",String(t.contract_type??"")],["合同金额",String(t.contract_amount??"")],["签订日期",String(t.sign_date??"")],["交货期限",String(t.delivery_deadline??"")],["付款进度",String(t.payment_progress??"")+"%"],["审批状态",String(t.approval_status??"")]],tables:i.length?[{title:"付款计划",headers:["付款单号","阶段","金额","状态"],rows:i.map(e=>[String(e.payment_no??""),String(e.payment_stage??""),String(e.payment_amount??""),String(e.status??"")])}]:void 0,signatures:["合同经办人","财务审核","分管院领导"]})}function _(t){const i=t.items??[],e=t.members??[],s=i.filter(n=>n.is_passed).length;r({title:"医疗设备安装验收单",docNo:String(t.acceptance_no??""),fields:[["验收日期",String(t.acceptance_date??"")],["质检通过",t.quality_check_passed?"是":"否"],["安装完成",t.installation_completed?"是":"否"],["清单通过",`${s}/${i.length}`],["论证摘要",String(t.argument_summary??"")]],tables:[...i.length?[{title:"验收清单",headers:["检查项目","验收标准","结果","通过"],rows:i.map(n=>[String(n.item_name??""),String(n.check_standard??""),String(n.check_result??""),n.is_passed?"是":"否"])}]:[],...e.length?[{title:"验收小组",headers:["角色","姓名","备注"],rows:e.map(n=>[String(n.member_role??""),String(n.member_name??""),String(n.remark??"")])}]:[]],signatures:["质控签字","工程签字","临床签字","设备科签字"]})}export{u as a,h as b,_ as c,b as p};
