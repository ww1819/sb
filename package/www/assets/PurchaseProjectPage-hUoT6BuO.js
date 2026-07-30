import{d as Le,m as Oe,g as k,ae as Pe,o as E,c as Ee,f as n,w as i,s as Ie,A as Q,j as c,z as w,x as Ye,e as d,p as Ae,y as He,r as u,k as We,a0 as I,E as m,ad as Ze,G as Ge,_ as Je}from"./index-Dve33j3G.js";import{S as Ke}from"./SystemPageCard-DGQcU--P.js";import{T as F,a as Qe,P as Xe,b as re,u as et,p as tt}from"./ModulePage-DWrf2rtz.js";import{u as at}from"./useDict-XnSL-HUY.js";import"./pageRegistry-aAM-mZFE.js";import"./useSystemTableHeight-7mtOILqU.js";function X(g){return g.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;")}function se(g,f){const v=g==null?"":String(g).trim(),b=v?X(v).replace(/\n/g,"<br/>"):"&nbsp;";return`<div class="box" style="height:${f}px;min-height:${f}px;line-height:1.6;">${b}</div>`}function Y(g){const f=g==null?"":String(g).trim();return f?X(f):"&nbsp;"}function be(g,f){const v="设备购置询价议价会议记录",b=f==="word",h=130,T=170,V=170,C=b?`<!--[if gte mso 9]><xml>
<w:WordDocument>
  <w:View>Print</w:View>
  <w:Zoom>100</w:Zoom>
  <w:DoNotOptimizeForBrowser/>
</w:WordDocument>
</xml><![endif]-->`:"";return`<!DOCTYPE html>
<html xmlns:o="urn:schemas-microsoft-com:office:office"
      xmlns:w="urn:schemas-microsoft-com:office:word"
      xmlns="http://www.w3.org/TR/REC-html40">
<head>
<meta charset="utf-8"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<title>${X(v)}</title>
${C}
<style>
  /* A4：210mm x 297mm */
  @page {
    size: A4 portrait;
    margin: 18mm 16mm 18mm 16mm;
  }
  ${b?`
  @page Section1 {
    size: 595.3pt 841.9pt;
    margin: 56.7pt 50.4pt 56.7pt 50.4pt;
  }
  div.Section1 { page: Section1; }
  `:""}
  html, body {
    margin: 0;
    padding: 0;
    background: #fff;
    color: #111;
    font-family: "SimSun", "宋体", "Songti SC", serif;
    font-size: 14pt;
  }
  .sheet {
    width: 100%;
    box-sizing: border-box;
    ${b?"":"min-height: 261mm;"}
    padding: ${b?"0":"4mm 2mm"};
  }
  .title {
    text-align: center;
    font-size: 18pt;
    font-weight: bold;
    letter-spacing: 3px;
    margin: 0 0 14pt;
    line-height: 1.4;
  }
  table.doc {
    width: 100%;
    border-collapse: collapse;
    table-layout: fixed;
    border: 1.5pt solid #111;
  }
  table.doc th,
  table.doc td {
    border: 1pt solid #222;
    padding: 8pt 10pt;
    vertical-align: middle;
    font-size: 12pt;
    line-height: 1.5;
    color: #111;
  }
  table.doc th {
    width: 92pt;
    background: #f5f5f5;
    font-weight: bold;
    text-align: center;
    white-space: nowrap;
  }
  table.doc td.val {
    word-break: break-word;
  }
  table.doc tr.tall th,
  table.doc tr.tall td {
    vertical-align: top;
  }
  .box {
    width: 100%;
    box-sizing: border-box;
    overflow: hidden;
    word-break: break-word;
  }
  @media print {
    body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    table.doc th { background: #f5f5f5 !important; }
  }
</style>
</head>
<body>
<div class="Section1 sheet">
  <div class="title">${X(v)}</div>
  <table class="doc" cellspacing="0" cellpadding="0">
    <colgroup>
      <col style="width:92pt"/>
      <col style="width:45%"/>
      <col style="width:92pt"/>
      <col style="width:45%"/>
    </colgroup>
    <tr style="height:36pt">
      <th>会议地点</th>
      <td class="val">${Y(g.meetingLocation)}</td>
      <th>会议时间</th>
      <td class="val">${Y(g.meetingTime)}</td>
    </tr>
    <tr style="height:36pt">
      <th>申请科室</th>
      <td class="val">${Y(g.deptName)}</td>
      <th>设备名称</th>
      <td class="val">${Y(g.deviceName)}</td>
    </tr>
    <tr style="height:36pt">
      <th>参与部门</th>
      <td class="val" colspan="3">${Y(g.participantDepts)}</td>
    </tr>
    <tr class="tall" style="height:${h}px">
      <th>设备科意见</th>
      <td class="val" colspan="3" height="${h}">${se(g.deptOpinion,h)}</td>
    </tr>
    <tr class="tall" style="height:${T}px">
      <th>会议内容</th>
      <td class="val" colspan="3" height="${T}">${se(g.meetingContent,T)}</td>
    </tr>
    <tr class="tall" style="height:${V}px">
      <th>会议结论</th>
      <td class="val" colspan="3" height="${V}">${se(g.meetingConclusion,V)}</td>
    </tr>
  </table>
</div>
${f==="print"?"<script>window.onload=function(){setTimeout(function(){window.print()},80)}<\/script>":""}
</body>
</html>`}function nt(g){const f=be(g,"print"),v=window.open("","_blank");return v?(v.document.open(),v.document.write(f),v.document.close(),!0):!1}function lt(g,f){const v=be(g,"word"),b=new Blob(["\uFEFF"+v],{type:"application/msword;charset=utf-8"}),h=document.createElement("a");h.href=URL.createObjectURL(b),h.download=f,h.click(),URL.revokeObjectURL(h.href)}const it={class:"purchase-project-page"},ot={class:"bargain-split"},rt={class:"bargain-left"},st={class:"bargain-left-scroll"},dt={class:"bargain-right"},ut={class:"bargain-a4"},pt={class:"bargain-table"},ct={colspan:"3"},mt={class:"bargain-tall"},gt={colspan:"3"},_t={class:"bargain-tall"},ft={colspan:"3"},vt={class:"bargain-tall"},bt={colspan:"3"},wt=Le({__name:"PurchaseProjectPage",setup(g){const{loadDict:f,resolveDictLabel:v}=at(),{selectedCount:b,selectedIds:h,syncFromTable:T,clearAll:V}=et(),C=u(!1),de=u([]),M=u(1),A=u(20),ue=u(0),N=u(""),ee=u(null),R=u(new Map),j=u(!1),te=u(!1),S=u("single"),q=u(null),L=u("同意审核"),H=u(!1),ae=u(!1),W=u(""),ne=u(""),U=u([]),pe=u(null),s=We({dept_name:"",device_name:"",bargain_meeting_location:"设备科",bargain_meeting_time:"",bargain_participant_depts:"",bargain_dept_opinion:"",bargain_meeting_content:"",bargain_meeting_conclusion:""}),O=u(!1),le=u(!1),ie=u(null),B=u("passed"),x=u("议价通过"),ce={prop:"estimated_price",label:"预算单价",type:"number"},me={prop:"quantity",label:"数量",type:"number"},we={prop:"total_price",label:"总价值",type:"number"},he={prop:"fund_source",label:"经费来源",dictType:"fund_source"},ye=Ge(()=>{const t=new Date,e=o=>String(o).padStart(2,"0");return`${t.getFullYear()}-${e(t.getMonth()+1)}-${e(t.getDate())}`});function D(t){if(t==null||t==="")return"-";const e=String(t),o=e.match(/^(\d{4}-\d{2}-\d{2})/);if(o)return o[1];const l=new Date(e);if(Number.isNaN(l.getTime()))return e;const p=y=>String(y).padStart(2,"0");return`${l.getFullYear()}-${p(l.getMonth()+1)}-${p(l.getDate())}`}function ke(t){return(M.value-1)*A.value+t+1}function xe(t){T(t);const e=new Map(R.value),o=new Set(t.map(l=>String(l.id)));for(const[l]of e)o.has(l)||e.delete(l);for(const l of t)l.id!=null&&e.set(String(l.id),l);R.value=e}async function $(){var t,e;C.value=!0;try{const{data:o}=await I.get("/purchase/project/page",{params:{page:M.value,size:A.value,keyword:N.value||void 0}});de.value=((t=o.data)==null?void 0:t.records)??[],ue.value=((e=o.data)==null?void 0:e.total)??0}finally{C.value=!1}}function ge(){M.value=1,V(ee.value),R.value=new Map,$()}function Se(){N.value="",ge()}function _e(){return h().filter(t=>{const e=R.value.get(t);return e?!P(e):!0})}function $e(t){if(P(t)){m.warning("该订单已审核，不能重复审核");return}S.value="single",q.value=t,L.value="同意审核",j.value=!0}function Ve(){if(b.value===0){m.warning("请先勾选要审核的明细");return}const t=_e();if(!t.length){m.warning("勾选的明细均已审核，无需重复审核");return}t.length<b.value&&m.info(`已跳过 ${b.value-t.length} 条已审核明细`),S.value="batch",q.value=null,L.value="同意审核",j.value=!0}async function Ce(){var l,p,y;const t=L.value.trim();if(!t){m.warning("请填写订单审核意见");return}const e=S.value==="single"?(l=q.value)!=null&&l.id?[String(q.value.id)]:[]:_e();if(!e.length){m.warning(S.value==="batch"?"勾选的明细均已审核，无需重复审核":"没有可审核的明细");return}te.value=!0;let o=0;try{for(const _ of e)await I.post(`/purchase/project/approved-items/${_}/order-review`,{comment:t}),o++;m.success(S.value==="batch"?`已审核 ${o} 条`:"已保存订单审核意见"),j.value=!1,S.value==="batch"&&(V(ee.value),R.value=new Map),$()}catch(_){const r=_;m.error(((y=(p=r==null?void 0:r.response)==null?void 0:p.data)==null?void 0:y.message)||`审核失败（已成功 ${o} 条）`),o>0&&$()}finally{te.value=!1}}function P(t){return t?!!(t.order_reviewed_at||t.order_review_comment&&String(t.order_review_comment).trim()):!1}function z(t){return t?!!(t.bargain_at||t.bargain_meeting_content||t.bargain_dept_opinion||t.bargain_meeting_conclusion):!1}function Re(t){return t?!!(t.bargain_record_url&&String(t.bargain_record_url).trim()):!1}function je(t,e){t.bargain_record_url=e}function Ue(t){const e=String(t),o={passed:"议价通过",rejected:"议价未通过"},l=Object.values(o),p=x.value.trim();(!p||l.includes(p))&&(x.value=e==="rejected"?o.rejected:o.passed)}function Be(t){if(!P(t)){m.warning("请先完成订单审核后再议价审核");return}if(!z(t)){m.warning("请先完成议价会议记录后再议价审核");return}if(!Re(t)){m.warning("请先上传议价记录附件后再议价审核");return}ie.value=t;const e=String(t.bargain_review_result||"");e==="rejected"?(B.value="rejected",x.value=String(t.bargain_review_comment||"议价未通过")):e==="passed"?(B.value="passed",x.value=String(t.bargain_review_comment||"议价通过")):(B.value="passed",x.value="议价通过"),O.value=!0}async function De(){var e,o;const t=ie.value;if(t!=null&&t.id){if(!x.value.trim()){m.warning("请填写议价建议");return}le.value=!0;try{const{data:l}=await I.post(`/purchase/project/approved-items/${t.id}/bargain-review`,{result:B.value,comment:x.value.trim()}),p=l.data??{};Object.assign(t,p),m.success("议价审核已保存"),O.value=!1,$()}catch(l){const p=l;m.error(((o=(e=p==null?void 0:p.response)==null?void 0:e.data)==null?void 0:o.message)||"议价审核失败")}finally{le.value=!1}}}function ze(t){W.value=String(t.id??"");const e=String(t.dept_name??ne.value??""),o=String(t.device_name??"");s.dept_name=e,s.device_name=o,s.bargain_meeting_location=String(t.bargain_meeting_location||"设备科"),s.bargain_meeting_time=D(t.bargain_meeting_time),s.bargain_meeting_time==="-"&&(s.bargain_meeting_time=ye.value),s.bargain_participant_depts=String(t.bargain_participant_depts||(e?`设备科，${e}`:"设备科")),s.bargain_dept_opinion=String(t.bargain_dept_opinion||""),s.bargain_meeting_content=String(t.bargain_meeting_content||(o?`${o} 议价`:"")),s.bargain_meeting_conclusion=String(t.bargain_meeting_conclusion||""),Ze(()=>{var l;(l=pe.value)==null||l.setCurrentRow(t)})}function fe(){return{meetingLocation:s.bargain_meeting_location,meetingTime:s.bargain_meeting_time,deptName:s.dept_name,deviceName:s.device_name,participantDepts:s.bargain_participant_depts,deptOpinion:s.bargain_dept_opinion,meetingContent:s.bargain_meeting_content,meetingConclusion:s.bargain_meeting_conclusion}}function Fe(){nt(fe())||m.warning("无法打开打印窗口，请检查浏览器是否拦截弹窗")}function Te(){lt(fe(),"设备购置询价议价会议记录表.doc"),m.success("已开始下载 Word 文档")}function Me(t){if(!P(t)){m.warning("请先完成订单审核后再议价");return}ne.value=String(t.dept_name??""),H.value=!0,U.value=[{...t,dept_name:ne.value}],ze(U.value[0])}async function Ne(){var t,e;if(W.value){if(!s.bargain_meeting_location.trim()){m.warning("请填写会议地点");return}if(!s.bargain_meeting_time){m.warning("请选择会议时间");return}ae.value=!0;try{const{data:o}=await I.post(`/purchase/project/approved-items/${W.value}/bargain`,{bargain_meeting_location:s.bargain_meeting_location,bargain_meeting_time:s.bargain_meeting_time,bargain_participant_depts:s.bargain_participant_depts,bargain_dept_opinion:s.bargain_dept_opinion,bargain_meeting_content:s.bargain_meeting_content,bargain_meeting_conclusion:s.bargain_meeting_conclusion}),l=o.data??{},p=U.value.findIndex(y=>String(y.id)===W.value);p>=0&&(U.value[p]={...U.value[p],...l,dept_name:s.dept_name,device_name:s.device_name}),m.success("议价记录已保存"),$()}catch(o){const l=o;m.error(((e=(t=l==null?void 0:l.response)==null?void 0:t.data)==null?void 0:e.message)||"保存失败")}finally{ae.value=!1}}}async function qe(){var e;const t=await tt(b.value,"导出");if(t)try{let o=[];if(t==="selected")o=h().map(r=>R.value.get(r)).filter(r=>!!r);else{const{data:r}=await I.get("/purchase/project/page",{params:{page:1,size:5e3,keyword:N.value||void 0}});o=((e=r.data)==null?void 0:e.records)??[]}const p=[["订单号","计划单号","年度","申请科室","设备名称","规格型号","预算单价","数量","总价值","提交日期","经费来源","购买用途","议价状态","议价记录","议价审核结果","议价建议","申请日期","审核建议","订单审核意见","品牌意向","备注","会议地点","会议时间","参与部门","设备科意见","会议内容","会议结论"].join(",")];for(const r of o){const Z=v("fund_source",r.fund_source)||String(r.fund_source??""),G=r.bargain_review_result==="passed"?"议价通过":r.bargain_review_result==="rejected"?"议价未通过":"",oe=[r.order_no,r.plan_code,r.plan_year,r.dept_name,r.device_name,r.specification,r.estimated_price,r.quantity,r.total_price,D(r.submitted_at),Z,r.purchase_purpose,z(r)?"已议价":"未议价",r.bargain_record_url,G,r.bargain_review_comment,D(r.fill_date),r.approval_comment,r.order_review_comment,r.brand_intent,r.plan_remark,r.bargain_meeting_location,D(r.bargain_meeting_time),r.bargain_participant_depts,r.bargain_dept_opinion,r.bargain_meeting_content,r.bargain_meeting_conclusion].map(J=>`"${J==null?"":String(J).replace(/"/g,'""')}"`);p.push(oe.join(","))}const y=new Blob(["\uFEFF"+p.join(`
`)],{type:"text/csv;charset=utf-8"}),_=document.createElement("a");_.href=URL.createObjectURL(y),_.download="purchase_approved_items_export.csv",_.click(),URL.revokeObjectURL(_.href),m.success(`已导出 ${o.length} 条`)}catch{m.error("导出失败")}}return Oe(async()=>{await f("fund_source"),$()}),(t,e)=>{const o=k("el-button"),l=k("el-table-column"),p=k("el-tag"),y=k("el-table"),_=k("el-form-item"),r=k("el-input"),Z=k("el-form"),G=k("el-radio"),oe=k("el-radio-group"),J=k("el-date-picker"),ve=Pe("loading");return E(),Ee("div",it,[n(Ke,{title:"设备采购计划表",loading:C.value,"show-pager":"",page:M.value,"onUpdate:page":e[1]||(e[1]=a=>M.value=a),size:A.value,"onUpdate:size":e[2]||(e[2]=a=>A.value=a),total:ue.value,onPageChange:$},{filterBar:i(()=>[n(Xe,{keyword:N.value,"onUpdate:keyword":e[0]||(e[0]=a=>N.value=a),placeholder:"订单号 / 计划单号 / 科室 / 设备名称",onSearch:ge,onReset:Se},{actions:i(()=>[n(o,{type:"primary",onClick:Ve},{default:i(()=>[...e[18]||(e[18]=[c("审核",-1)])]),_:1}),n(o,{onClick:qe},{default:i(()=>[...e[19]||(e[19]=[c("导出",-1)])]),_:1})]),_:1},8,["keyword"])]),default:i(()=>[Ie((E(),Q(y,{ref_key:"tableRef",ref:ee,data:de.value,stripe:"",class:"system-table","row-key":"id",onSelectionChange:xe},{default:i(()=>[n(l,{type:"selection",width:"48",fixed:"left","reserve-selection":""}),n(l,{type:"index",label:"序号",width:"64",align:"center",index:ke}),n(l,{prop:"order_no",label:"订单号","min-width":"150","show-overflow-tooltip":""},{default:i(({row:a})=>[c(w(a.order_no||"-"),1)]),_:1}),n(l,{prop:"plan_code",label:"计划单号","min-width":"150","show-overflow-tooltip":""}),n(l,{prop:"plan_year",label:"年度",width:"90"}),n(l,{prop:"dept_name",label:"申请科室","min-width":"120","show-overflow-tooltip":""}),n(l,{prop:"device_name",label:"设备名称","min-width":"140","show-overflow-tooltip":""}),n(l,{prop:"specification",label:"规格型号","min-width":"130","show-overflow-tooltip":""}),n(l,{prop:"estimated_price",label:"预算单价","min-width":"110",align:"right"},{default:i(({row:a})=>[n(F,{field:ce,value:a.estimated_price},null,8,["value"])]),_:1}),n(l,{prop:"quantity",label:"数量",width:"90",align:"right"},{default:i(({row:a})=>[n(F,{field:me,value:a.quantity},null,8,["value"])]),_:1}),n(l,{prop:"total_price",label:"总价值","min-width":"120",align:"right"},{default:i(({row:a})=>[n(F,{field:we,value:a.total_price},null,8,["value"])]),_:1}),n(l,{prop:"submitted_at",label:"提交日期",width:"120"},{default:i(({row:a})=>[c(w(D(a.submitted_at)),1)]),_:1}),n(l,{prop:"fund_source",label:"经费来源","min-width":"110","show-overflow-tooltip":""},{default:i(({row:a})=>[n(F,{field:he,value:a.fund_source},null,8,["value"])]),_:1}),n(l,{prop:"purchase_purpose",label:"购买用途","min-width":"140","show-overflow-tooltip":""}),n(l,{label:"议价状态",width:"90",align:"center"},{default:i(({row:a})=>[n(p,{type:z(a)?"success":"info",size:"small",effect:"plain"},{default:i(()=>[c(w(z(a)?"已议价":"未议价"),1)]),_:2},1032,["type"])]),_:1}),n(l,{label:"议价记录",width:"140",align:"center"},{default:i(({row:a})=>[n(Qe,{value:a.bargain_record_url,prop:"bargain_record_url","row-id":String(a.id),"save-base":"/purchase/project/approved-items",onUpdated:K=>je(a,K)},null,8,["value","row-id","onUpdated"])]),_:1}),n(l,{prop:"fill_date",label:"申请日期",width:"120"},{default:i(({row:a})=>[c(w(D(a.fill_date)),1)]),_:1}),n(l,{prop:"approval_comment",label:"审核建议","min-width":"140","show-overflow-tooltip":""},{default:i(({row:a})=>[c(w(a.approval_comment||"-"),1)]),_:1}),n(l,{prop:"order_review_comment",label:"订单审核意见","min-width":"140","show-overflow-tooltip":""},{default:i(({row:a})=>[c(w(a.order_review_comment||"-"),1)]),_:1}),n(l,{prop:"brand_intent",label:"品牌意向","min-width":"120","show-overflow-tooltip":""}),n(l,{prop:"plan_remark",label:"备注","min-width":"140","show-overflow-tooltip":""},{default:i(({row:a})=>[c(w(a.plan_remark||"-"),1)]),_:1}),n(l,{label:"操作",width:"220",fixed:"right",align:"center"},{default:i(({row:a})=>[P(a)?Ye("",!0):(E(),Q(o,{key:0,link:"",type:"primary",onClick:K=>$e(a)},{default:i(()=>[...e[20]||(e[20]=[c("审核",-1)])]),_:1},8,["onClick"])),n(o,{link:"",type:"primary",onClick:K=>Be(a)},{default:i(()=>[...e[21]||(e[21]=[c("议价审核",-1)])]),_:1},8,["onClick"]),n(o,{link:"",type:"primary",onClick:K=>Me(a)},{default:i(()=>[...e[22]||(e[22]=[c("议价",-1)])]),_:1},8,["onClick"])]),_:1})]),_:1},8,["data"])),[[ve,C.value]])]),_:1},8,["loading","page","size","total"]),n(re,{modelValue:j.value,"onUpdate:modelValue":e[5]||(e[5]=a=>j.value=a),title:"订单审核",size:"sm"},{footer:i(()=>[n(o,{onClick:e[4]||(e[4]=a=>j.value=!1)},{default:i(()=>[...e[23]||(e[23]=[c("取消",-1)])]),_:1}),n(o,{type:"primary",loading:te.value,onClick:Ce},{default:i(()=>[...e[24]||(e[24]=[c("确认审核",-1)])]),_:1},8,["loading"])]),default:i(()=>[n(Z,{"label-width":"110px"},{default:i(()=>[S.value==="single"?(E(),Q(_,{key:0,label:"订单号"},{default:i(()=>{var a;return[d("span",null,w(((a=q.value)==null?void 0:a.order_no)||"-"),1)]}),_:1})):(E(),Q(_,{key:1,label:"已选"},{default:i(()=>[d("span",null,w(Ae(b))+" 条明细",1)]),_:1})),n(_,{label:"订单审核意见",required:""},{default:i(()=>[n(r,{modelValue:L.value,"onUpdate:modelValue":e[3]||(e[3]=a=>L.value=a),type:"textarea",rows:4,maxlength:"500","show-word-limit":"",placeholder:"请填写订单审核意见"},null,8,["modelValue"])]),_:1})]),_:1})]),_:1},8,["modelValue"]),n(re,{modelValue:O.value,"onUpdate:modelValue":e[9]||(e[9]=a=>O.value=a),title:"议价审核",size:"sm"},{footer:i(()=>[n(o,{onClick:e[8]||(e[8]=a=>O.value=!1)},{default:i(()=>[...e[27]||(e[27]=[c("取消",-1)])]),_:1}),n(o,{type:"primary",loading:le.value,onClick:De},{default:i(()=>[...e[28]||(e[28]=[c(" 确认 ",-1)])]),_:1},8,["loading"])]),default:i(()=>[n(Z,{"label-width":"110px"},{default:i(()=>[n(_,{label:"订单号"},{default:i(()=>{var a;return[d("span",null,w(((a=ie.value)==null?void 0:a.order_no)||"-"),1)]}),_:1}),n(_,{label:"审核结果",required:""},{default:i(()=>[n(oe,{modelValue:B.value,"onUpdate:modelValue":e[6]||(e[6]=a=>B.value=a),onChange:Ue},{default:i(()=>[n(G,{value:"passed"},{default:i(()=>[...e[25]||(e[25]=[c("议价通过",-1)])]),_:1}),n(G,{value:"rejected"},{default:i(()=>[...e[26]||(e[26]=[c("议价未通过",-1)])]),_:1})]),_:1},8,["modelValue"])]),_:1}),n(_,{label:"议价建议",required:""},{default:i(()=>[n(r,{modelValue:x.value,"onUpdate:modelValue":e[7]||(e[7]=a=>x.value=a),type:"textarea",rows:4,maxlength:"500","show-word-limit":"",placeholder:"请填写议价建议"},null,8,["modelValue"])]),_:1})]),_:1})]),_:1},8,["modelValue"]),n(re,{modelValue:H.value,"onUpdate:modelValue":e[17]||(e[17]=a=>H.value=a),title:"询价议价",size:"xl"},{"header-actions":i(()=>[n(o,{plain:"",onClick:Te},{default:i(()=>[...e[29]||(e[29]=[c("下载模板",-1)])]),_:1}),n(o,{plain:"",onClick:Fe},{default:i(()=>[...e[30]||(e[30]=[c("打印",-1)])]),_:1})]),footer:i(()=>[n(o,{onClick:e[16]||(e[16]=a=>H.value=!1)},{default:i(()=>[...e[40]||(e[40]=[c("取消",-1)])]),_:1}),n(o,{type:"primary",loading:ae.value,onClick:Ne},{default:i(()=>[...e[41]||(e[41]=[c("保存",-1)])]),_:1},8,["loading"])]),default:i(()=>[d("div",ot,[d("div",rt,[d("div",st,[n(y,{ref_key:"bargainTableRef",ref:pe,data:U.value,size:"small",border:"","highlight-current-row":"","row-key":"id",class:"bargain-item-table",style:{width:"784px"}},{default:i(()=>[n(l,{type:"index",label:"序号",width:"52",align:"center"}),n(l,{label:"状态",width:"78",align:"center"},{default:i(({row:a})=>[d("span",{class:He(z(a)?"st-done":"st-todo")},w(z(a)?"已议价":"未议价"),3)]),_:1}),n(l,{prop:"dept_name",label:"申请科室",width:"110","show-overflow-tooltip":""}),n(l,{prop:"device_name",label:"设备名称",width:"140","show-overflow-tooltip":""}),n(l,{prop:"estimated_price",label:"预计单价",width:"100",align:"right"},{default:i(({row:a})=>[n(F,{field:ce,value:a.estimated_price},null,8,["value"])]),_:1}),n(l,{prop:"quantity",label:"数量",width:"72",align:"right"},{default:i(({row:a})=>[n(F,{field:me,value:a.quantity},null,8,["value"])]),_:1}),n(l,{prop:"unit",label:"单位",width:"72",align:"center","show-overflow-tooltip":""},{default:i(({row:a})=>[c(w(a.unit||"-"),1)]),_:1}),n(l,{prop:"specification",label:"规格型号",width:"160","show-overflow-tooltip":""})]),_:1},8,["data"])])]),d("div",dt,[d("div",ut,[e[39]||(e[39]=d("h3",{class:"bargain-a4__title"},"设备购置询价议价会议记录",-1)),d("table",pt,[d("tbody",null,[d("tr",null,[e[31]||(e[31]=d("th",null,"会议地点",-1)),d("td",null,[n(r,{modelValue:s.bargain_meeting_location,"onUpdate:modelValue":e[10]||(e[10]=a=>s.bargain_meeting_location=a),placeholder:"会议地点"},null,8,["modelValue"])]),e[32]||(e[32]=d("th",null,"会议时间",-1)),d("td",null,[n(J,{modelValue:s.bargain_meeting_time,"onUpdate:modelValue":e[11]||(e[11]=a=>s.bargain_meeting_time=a),type:"date","value-format":"YYYY-MM-DD",placeholder:"选择日期",style:{width:"100%"}},null,8,["modelValue"])])]),d("tr",null,[e[33]||(e[33]=d("th",null,"申请科室",-1)),d("td",null,w(s.dept_name||"-"),1),e[34]||(e[34]=d("th",null,"设备名称",-1)),d("td",null,w(s.device_name||"-"),1)]),d("tr",null,[e[35]||(e[35]=d("th",null,"参与部门",-1)),d("td",ct,[n(r,{modelValue:s.bargain_participant_depts,"onUpdate:modelValue":e[12]||(e[12]=a=>s.bargain_participant_depts=a),placeholder:"参与部门"},null,8,["modelValue"])])]),d("tr",mt,[e[36]||(e[36]=d("th",null,"设备科意见",-1)),d("td",gt,[n(r,{modelValue:s.bargain_dept_opinion,"onUpdate:modelValue":e[13]||(e[13]=a=>s.bargain_dept_opinion=a),type:"textarea",rows:6,placeholder:"请填写设备科意见"},null,8,["modelValue"])])]),d("tr",_t,[e[37]||(e[37]=d("th",null,"会议内容",-1)),d("td",ft,[n(r,{modelValue:s.bargain_meeting_content,"onUpdate:modelValue":e[14]||(e[14]=a=>s.bargain_meeting_content=a),type:"textarea",rows:8,placeholder:"请填写会议内容"},null,8,["modelValue"])])]),d("tr",vt,[e[38]||(e[38]=d("th",null,"会议结论",-1)),d("td",bt,[n(r,{modelValue:s.bargain_meeting_conclusion,"onUpdate:modelValue":e[15]||(e[15]=a=>s.bargain_meeting_conclusion=a),type:"textarea",rows:8,placeholder:"请填写会议结论"},null,8,["modelValue"])])])])])])])])]),_:1},8,["modelValue"])])}}}),Vt=Je(wt,[["__scopeId","data-v-d7cc4eb0"]]);export{Vt as default};
