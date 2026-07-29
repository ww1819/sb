const u="医疗机构（采购管理）";function e(t){return t.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}function F(t){const n=t.headers.map(l=>`<th>${e(l)}</th>`).join(""),i=t.rows.map(l=>`<tr>${l.map(d=>`<td>${e(d??"")}</td>`).join("")}</tr>`).join("");return`${t.title?`<h3 class="sub">${e(t.title)}</h3>`:""}<table class="data"><thead><tr>${n}</tr></thead><tbody>${i}</tbody></table>`}function _(t){var g;const n=t.hospitalName||u,i=new Date().toLocaleDateString("zh-CN"),p=(g=t.signatures)!=null&&g.length?`<div class="signatures">${t.signatures.map(a=>`<div class="sig-cell"><div class="sig-line"></div><div>${e(a)}</div></div>`).join("")}</div>`:"",l=(t.tables??[]).map(F).join(""),d=`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${e(t.title)}</title>
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
  <div class="hospital">${e(n)}</div>
  <div class="title">${e(t.title)}</div>
  ${t.subtitle?`<div class="subtitle">${e(t.subtitle)}</div>`:""}
</div>
<div class="meta">
  <span>单据编号：${e(t.docNo??"-")}</span>
  <span>打印日期：${i}</span>
</div>
<table class="info">${t.fields.map(([a,r])=>`<tr><td class="label">${e(a)}</td><td>${e(r??"")}</td></tr>`).join("")}</table>
${l}
${p}
<div class="footer">${e(t.footerNote??"本单据由 MEIS 采购管理系统自动生成")}</div>
<script>window.onload=function(){window.print()}<\/script></body></html>`,s=window.open("","_blank");s&&(s.document.write(d),s.document.close())}function E(t){const n=t.items??[];_({title:"医疗设备采购计划申报表",docNo:String(t.plan_code??""),subtitle:`${t.plan_year??""} 年度采购计划`,fields:[["业务链编号",String(t.business_chain_no??"")],["计划年度",String(t.plan_year??"")],["计划类型",String(t.plan_type??"")],["资金来源",String(t.fund_source??"")],["预算总额",String(t.total_budget??"")],["大型设备",t.is_large_equipment?"是":"否"],["审批状态",String(t.approval_status??"")],["论证说明",String(t.justification??"")]],tables:n.length?[{title:"计划明细",headers:["设备名称","数量","单位","预估单价","金额小计"],rows:n.map(i=>[String(i.device_name??""),String(i.quantity??""),String(i.unit??""),String(i.estimated_price??""),String(i.total_price??"")])}]:void 0,signatures:["申报科室负责人","医学装备科","分管院领导"]})}function O(t){const n=t.payments??[];_({title:"医疗设备采购合同审批单",docNo:String(t.contract_code??""),fields:[["合同名称",String(t.contract_name??"")],["合同类型",String(t.contract_type??"")],["合同金额",String(t.contract_amount??"")],["签订日期",String(t.sign_date??"")],["交货期限",String(t.delivery_deadline??"")],["付款进度",String(t.payment_progress??"")+"%"],["审批状态",String(t.approval_status??"")]],tables:n.length?[{title:"付款计划",headers:["付款单号","阶段","金额","状态"],rows:n.map(i=>[String(i.payment_no??""),String(i.payment_stage??""),String(i.payment_amount??""),String(i.status??"")])}]:void 0,signatures:["合同经办人","财务审核","分管院领导"]})}function L(t){const n=t.items??[],i=t.members??[],p=n.filter(l=>l.is_passed).length;_({title:"医疗设备安装验收单",docNo:String(t.acceptance_no??""),fields:[["验收日期",String(t.acceptance_date??"")],["质检通过",t.quality_check_passed?"是":"否"],["安装完成",t.installation_completed?"是":"否"],["清单通过",`${p}/${n.length}`],["论证摘要",String(t.argument_summary??"")]],tables:[...n.length?[{title:"验收清单",headers:["检查项目","验收标准","结果","通过"],rows:n.map(l=>[String(l.item_name??""),String(l.check_standard??""),String(l.check_result??""),l.is_passed?"是":"否"])}]:[],...i.length?[{title:"验收小组",headers:["角色","姓名","备注"],rows:i.map(l=>[String(l.member_role??""),String(l.member_name??""),String(l.remark??"")])}]:[]],signatures:["质控签字","工程签字","临床签字","设备科签字"]})}function v(t){if(!Number.isFinite(t))return"";const n=t<0,i=Math.round(Math.abs(t)*100);if(i===0)return"零元整";const p=["零","壹","贰","叁","肆","伍","陆","柒","捌","玖"],l=["","拾","佰","仟"],d=["","万","亿"],s=Math.floor(i/100),g=Math.floor(i%100/10),a=i%10;let r="";if(s>0){const o=String(s),$=[];let N=0;for(let h=o.length;h>0;h-=4,N++){const D=Math.max(0,h-4),w=o.slice(D,h);let b="",S=!1;for(let f=0;f<w.length;f++){const z=Number(w[f]),q=l[w.length-1-f];z===0?S=!0:(S&&(b+="零"),S=!1,b+=p[z]+q)}b&&$.unshift(b+d[N])}r=$.join("").replace(/零+/g,"零").replace(/零$/,"")+"元"}let c="";return g===0&&a===0?c="整":(g>0?c+=p[g]+"角":a>0&&s>0&&(c+="零"),a>0&&(c+=p[a]+"分")),(n?"负":"")+(r||"零元")+c}function m(t,n=2){if(t==null||t==="")return"";const i=Number(t);return Number.isFinite(i)?i.toFixed(n):String(t)}function x(t){if(t==null||t==="")return"";const n=String(t);return n.length>=10?n.slice(0,10):n}function j(t,n){const i=n||u,p=t.items??[],l=new Date().toLocaleDateString("zh-CN").replace(/\//g,"-");let d=0,s=0;const g=p.map(a=>{const r=Number(a.quantity??0),c=Number(a.unit_price??0);let o=Number(a.total_price);return Number.isFinite(o)||(o=Number.isFinite(r)&&Number.isFinite(c)?r*c:0),Number.isFinite(r)&&(d+=r),Number.isFinite(o)&&(s+=o),{name:String(a.device_name??""),spec:String(a.specification??a.model??""),batch:String(a.serial_number??""),qty:Number.isFinite(r)?String(r):"",price:m(c,2),amount:m(o,2)}});return!g.length&&t.total_amount!=null&&(s=Number(t.total_amount)||0),{hospital:i,warehouse:String(t.warehouse_name??""),docNo:String(t.entry_no??""),approvedAt:x(t.approved_at??t.entry_date),operator:String(t.created_by_name??t.operator_name??""),approver:String(t.approved_by_name??""),printDate:l,chineseTotal:v(Math.round(s*100)/100),qtySum:d?String(d):"",amountSum:m(s,2),rows:g}}function y(t){var d;const n=document.createElement("iframe");n.setAttribute("aria-hidden","true"),n.style.cssText="position:fixed;right:0;bottom:0;width:0;height:0;border:0;opacity:0;pointer-events:none",document.body.appendChild(n);const i=n.contentDocument||((d=n.contentWindow)==null?void 0:d.document);if(!i)return document.body.removeChild(n),!1;i.open(),i.write(t),i.close();const p=n.contentWindow;if(!p)return document.body.removeChild(n),!1;const l=()=>{setTimeout(()=>{n.parentNode&&n.parentNode.removeChild(n)},800)};return p.focus(),setTimeout(()=>{try{p.print()}finally{l()}},80),!0}function R(t,n){const i=j(t,n);return y(T(i))}function T(t){const n=t.rows.map(i=>`<tr>
      <td class="left">${e(i.name)}</td>
      <td class="left">${e(i.spec)}</td>
      <td>${e(i.batch)}</td>
      <td class="num">${e(i.qty)}</td>
      <td class="num">${e(i.price)}</td>
      <td class="num">${e(i.amount)}</td>
    </tr>`).join("");return`<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备入库单</title>
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
  <div class="title">${e(t.hospital)}设备入库单</div>
  <div class="meta-row">
    <div class="cell">仓库：${e(t.warehouse||"-")}</div>
    <div class="cell center"></div>
    <div class="cell right">单据号：${e(t.docNo||"-")}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${e(t.approvedAt||"-")}</div>
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
      ${n||'<tr><td colspan="6" style="height:36px"></td></tr>'}
      <tr>
        <td class="total-label" colspan="3">合计：${e(t.chineseTotal)}</td>
        <td class="num">${e(t.qtySum)}</td>
        <td></td>
        <td class="num">${e(t.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">入库经办人：${e(t.operator)}</div>
    <div class="cell center">审核人：${e(t.approver)}</div>
    <div class="cell right">打印日期：${e(t.printDate)}</div>
  </div>
</div>
</body></html>`}function Y(t,n){const i=A(t,n);return y(C(i))}function A(t,n){const i=n||u,p=t.items??[],l=new Date().toLocaleDateString("zh-CN").replace(/\//g,"-");let d=0,s=0;const g=p.map(a=>{const r=Number(a.quantity??0),c=Number(a.unit_price??0);let o=Number(a.total_price);return Number.isFinite(o)||(o=Number.isFinite(r)&&Number.isFinite(c)?r*c:0),Number.isFinite(r)&&(d+=r),Number.isFinite(o)&&(s+=o),{name:String(a.device_name??""),spec:String(a.specification??a.model??""),batch:String(a.serial_number??""),qty:Number.isFinite(r)?String(r):"",price:m(c,2),amount:m(o,2)}});return!g.length&&t.total_amount!=null&&(s=Number(t.total_amount)||0),{hospital:i,warehouse:String(t.warehouse_name??""),supplier:String(t.supplier_name??""),docNo:String(t.return_no??""),reason:String(t.reason??""),approvedAt:x(t.approved_at??t.return_date),operator:String(t.created_by_name??""),approver:String(t.approved_by_name??""),printDate:l,chineseTotal:v(Math.round(s*100)/100),qtySum:d?String(d):"",amountSum:m(s,2),rows:g}}function C(t){const n=t.rows.map(i=>`<tr>
      <td class="left">${e(i.name)}</td>
      <td class="left">${e(i.spec)}</td>
      <td>${e(i.batch)}</td>
      <td class="num">${e(i.qty)}</td>
      <td class="num">${e(i.price)}</td>
      <td class="num">${e(i.amount)}</td>
    </tr>`).join("");return`<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备退货单</title>
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
  <div class="title">${e(t.hospital)}设备退货单</div>
  <div class="meta-row">
    <div class="cell">仓库：${e(t.warehouse||"-")}</div>
    <div class="cell center">供应商：${e(t.supplier||"-")}</div>
    <div class="cell right">单据号：${e(t.docNo||"-")}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${e(t.approvedAt||"-")}</div>
    <div class="cell">退货原因：${e(t.reason||"-")}</div>
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
      ${n||'<tr><td colspan="6" style="height:36px"></td></tr>'}
      <tr>
        <td class="total-label" colspan="3">合计：${e(t.chineseTotal)}</td>
        <td class="num">${e(t.qtySum)}</td>
        <td></td>
        <td class="num">${e(t.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">制单人：${e(t.operator)}</div>
    <div class="cell center">审核人：${e(t.approver)}</div>
    <div class="cell right">打印日期：${e(t.printDate)}</div>
  </div>
</div>
</body></html>`}function I(t,n){const i=P(t,n);return y(M(i))}function P(t,n){const i=n||u,p=t.items??[],l=new Date().toLocaleDateString("zh-CN").replace(/\//g,"-");let d=0,s=0;const g=p.map(a=>{const r=Number(a.quantity??0),c=Number(a.unit_price??0);let o=Number(a.total_price);return Number.isFinite(o)||(o=Number.isFinite(r)&&Number.isFinite(c)?r*c:0),Number.isFinite(r)&&(d+=r),Number.isFinite(o)&&(s+=o),{name:String(a.device_name??""),spec:String(a.specification??a.model??""),batch:String(a.serial_number??""),qty:Number.isFinite(r)?String(r):"",price:m(c,2),amount:m(o,2)}});return!g.length&&t.total_amount!=null&&(s=Number(t.total_amount)||0),{hospital:i,warehouse:String(t.warehouse_name??""),dept:String(t.dept_name??""),docNo:String(t.outbound_no??""),receiver:String(t.receiver_name??""),purpose:String(t.purpose??""),approvedAt:x(t.approved_at??t.outbound_date),operator:String(t.created_by_name??""),approver:String(t.approved_by_name??""),printDate:l,chineseTotal:v(Math.round(s*100)/100),qtySum:d?String(d):"",amountSum:m(s,2),rows:g}}function M(t){const n=t.rows.map(i=>`<tr>
      <td class="left">${e(i.name)}</td>
      <td class="left">${e(i.spec)}</td>
      <td>${e(i.batch)}</td>
      <td class="num">${e(i.qty)}</td>
      <td class="num">${e(i.price)}</td>
      <td class="num">${e(i.amount)}</td>
    </tr>`).join("");return`<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备出库单</title>
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
  <div class="title">${e(t.hospital)}设备出库单</div>
  <div class="meta-row">
    <div class="cell">仓库：${e(t.warehouse||"-")}</div>
    <div class="cell center">科室：${e(t.dept||"-")}</div>
    <div class="cell right">单据号：${e(t.docNo||"-")}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${e(t.approvedAt||"-")}</div>
    <div class="cell">领用人：${e(t.receiver||"-")}</div>
    <div class="cell right">用途：${e(t.purpose||"-")}</div>
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
      ${n||'<tr><td colspan="6" style="height:36px"></td></tr>'}
      <tr>
        <td class="total-label" colspan="3">合计：${e(t.chineseTotal)}</td>
        <td class="num">${e(t.qtySum)}</td>
        <td></td>
        <td class="num">${e(t.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">制单人：${e(t.operator)}</div>
    <div class="cell center">审核人：${e(t.approver)}</div>
    <div class="cell right">打印日期：${e(t.printDate)}</div>
  </div>
</div>
</body></html>`}function G(t,n){const i=H(t,n);return y(k(i))}function H(t,n){const i=n||u,p=t.items??[],l=new Date().toLocaleDateString("zh-CN").replace(/\//g,"-");let d=0,s=0;const g=p.map(a=>{const r=Number(a.quantity??0),c=Number(a.unit_price??0);let o=Number(a.total_price);return Number.isFinite(o)||(o=Number.isFinite(r)&&Number.isFinite(c)?r*c:0),Number.isFinite(r)&&(d+=r),Number.isFinite(o)&&(s+=o),{name:String(a.device_name??""),spec:String(a.specification??a.model??""),batch:String(a.serial_number??""),qty:Number.isFinite(r)?String(r):"",price:m(c,2),amount:m(o,2)}});return!g.length&&t.total_amount!=null&&(s=Number(t.total_amount)||0),{hospital:i,warehouse:String(t.warehouse_name??""),dept:String(t.dept_name??""),docNo:String(t.return_no??""),reason:String(t.reason??""),approvedAt:x(t.approved_at??t.return_date),operator:String(t.created_by_name??""),approver:String(t.approved_by_name??""),printDate:l,chineseTotal:v(Math.round(s*100)/100),qtySum:d?String(d):"",amountSum:m(s,2),rows:g}}function k(t){const n=t.rows.map(i=>`<tr>
      <td class="left">${e(i.name)}</td>
      <td class="left">${e(i.spec)}</td>
      <td>${e(i.batch)}</td>
      <td class="num">${e(i.qty)}</td>
      <td class="num">${e(i.price)}</td>
      <td class="num">${e(i.amount)}</td>
    </tr>`).join("");return`<!DOCTYPE html><html><head><meta charset="utf-8"><title>设备退库单</title>
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
  <div class="title">${e(t.hospital)}设备退库单</div>
  <div class="meta-row">
    <div class="cell">仓库：${e(t.warehouse||"-")}</div>
    <div class="cell center">科室：${e(t.dept||"-")}</div>
    <div class="cell right">单据号：${e(t.docNo||"-")}</div>
  </div>
  <div class="meta-row">
    <div class="cell">审核时间：${e(t.approvedAt||"-")}</div>
    <div class="cell">退库原因：${e(t.reason||"-")}</div>
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
      ${n||'<tr><td colspan="6" style="height:36px"></td></tr>'}
      <tr>
        <td class="total-label" colspan="3">合计：${e(t.chineseTotal)}</td>
        <td class="num">${e(t.qtySum)}</td>
        <td></td>
        <td class="num">${e(t.amountSum)}</td>
      </tr>
    </tbody>
  </table>
  <div class="footer">
    <div class="cell">制单人：${e(t.operator)}</div>
    <div class="cell center">审核人：${e(t.approver)}</div>
    <div class="cell right">打印日期：${e(t.printDate)}</div>
  </div>
</div>
</body></html>`}export{O as a,L as b,R as c,I as d,G as e,Y as f,E as p};
