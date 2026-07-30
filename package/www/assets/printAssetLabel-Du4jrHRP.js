import{b as p}from"./browser-CjSdxGTc.js";import{a0 as d}from"./index-Dve33j3G.js";import{k as b,r as f}from"./ModulePage-DWrf2rtz.js";function o(e){return e.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")}function m(e){if(e==null||e==="")return"";const t=String(e);return t.length>=10?t.slice(0,10):t}function g(e){return[e.specification,e.model].filter(a=>a!=null&&String(a).trim()!=="").map(a=>String(a).trim()).join(" ")}async function h(){var e,t;try{const{data:a}=await d.get("/system/campuses",{params:{limit:1}}),i=(t=(((e=a.data)==null?void 0:e.records)??a.data??[])[0])==null?void 0:t.campus_name;if(i)return String(i)}catch{}return"医疗机构"}async function u(e,t){const a=String(e.device_code??"").trim();return{hospitalName:t,serialNumber:String(e.serial_number??""),deviceName:String(e.device_name??""),specModel:g(e),recordDate:m(e.enable_date??e.acceptance_date??e.purchase_date),useDept:f("department",e.dept_id)||"",deviceCode:a}}function w(e,t){const a=o(e.hospitalName||"医疗机构"),i=[["序列号：",e.serialNumber||""],["资产名称：",e.deviceName||""],["规格型号：",e.specModel||""],["入账日期：",e.recordDate||""],["使用科室：",e.useDept||""]].map(([r,l])=>`<div class="label-row"><span class="label-key">${o(r)}</span><span class="label-val">${o(l)}</span></div>`).join("");return`<div class="asset-label">
    <div class="asset-label__title">${a}</div>
    <div class="asset-label__body">
      <div class="asset-label__fields">${i}</div>
      <div class="asset-label__qr">
        ${t?`<img src="${t}" alt="二维码" />`:""}
      </div>
    </div>
  </div>`}function x(e){return`<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>资产标签打印</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 24px;
      font-family: SimSun, "Songti SC", serif;
      color: #000;
      background: #fff;
    }
    .toolbar {
      margin-bottom: 16px;
    }
    .print-btn {
      border: 1px solid #333;
      background: #fff;
      color: #d03030;
      font-size: 16px;
      padding: 4px 18px;
      cursor: pointer;
      font-family: inherit;
    }
    .print-btn:hover {
      background: #fff5f5;
    }
    .labels {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .asset-label {
      width: 520px;
      border: 1px solid #000;
      background: #fff;
      break-inside: avoid;
      page-break-inside: avoid;
    }
    .asset-label__title {
      text-align: center;
      font-size: 18px;
      font-weight: 700;
      padding: 10px 8px;
      border-bottom: 1px solid #000;
      letter-spacing: 1px;
    }
    .asset-label__body {
      display: flex;
      min-height: 188px;
    }
    .asset-label__fields {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
    }
    .label-row {
      display: flex;
      align-items: center;
      min-height: 37px;
      padding: 4px 10px;
      border-bottom: 1px solid #000;
      font-size: 15px;
      line-height: 1.4;
    }
    .label-row:last-child {
      border-bottom: none;
    }
    .label-key {
      flex-shrink: 0;
      white-space: nowrap;
    }
    .label-val {
      flex: 1;
      min-width: 0;
      word-break: break-all;
    }
    .asset-label__qr {
      width: 168px;
      border-left: 1px solid #000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 8px;
    }
    .asset-label__qr img {
      width: 148px;
      height: 148px;
      display: block;
    }
    @media print {
      body { padding: 0; }
      .toolbar { display: none; }
      .labels { gap: 0; }
      .asset-label {
        width: 100%;
        max-width: 520px;
        margin-bottom: 12px;
        page-break-after: always;
      }
      .asset-label:last-child {
        page-break-after: auto;
      }
    }
  </style>
</head>
<body>
  <div class="toolbar">
    <button type="button" class="print-btn" onclick="window.print()">打印</button>
  </div>
  <div class="labels">${e.join("")}</div>
</body>
</html>`}async function c(e){await b("department");const t=await h(),a=[];for(const r of e){const l=await u(r,t);let s="";try{s=await p.toDataURL(l.deviceCode,{width:200,margin:1})}catch{s=""}a.push(w(l,s))}const n=x(a),i=window.open("","_blank","width=640,height=720");if(!i)throw new Error("请允许弹出窗口以打印标签");i.document.write(n),i.document.close(),i.focus()}async function v(e){if(e)try{await d.post(`/asset/device/${e}/label/print`,{template_code:"asset_sticker"})}catch{}}async function y(e){if(!e.length)throw new Error("请先选择要打印的设备");if(e.filter(a=>!String(a.device_code??"").trim()).length)throw new Error("所选设备中存在无资产编码的记录，无法打印");await c(e);for(const a of e)await v(a.id)}async function D(e,t){if(!e)throw new Error("缺少盘点单");if(!t.length)throw new Error("请先选择要补打的设备");if(t.filter(i=>!String(i.device_code??"").trim()).length)throw new Error("所选设备中存在无资产编码的记录，无法打印");await c(t);const n=t.map(i=>i.id).filter(Boolean);try{await d.post(`/asset/inventory/${e}/label/print`,{item_ids:n,template_code:"asset_sticker"})}catch{}}async function L(e){await y([e])}export{y as a,D as b,L as p};
