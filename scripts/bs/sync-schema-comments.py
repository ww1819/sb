#!/usr/bin/env python3
"""Merge V2 ALTER columns into V1 CREATE TABLE; generate V4 comments; slim V2 to indexes only."""
from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TABLE_LABELS: dict[str, str] = {
    "campus": "院区",
    "building": "建筑物",
    "department": "科室",
    "sys_user": "系统用户",
    "sys_role": "系统角色",
    "sys_operation_log": "操作日志",
    "medical_device_category": "医疗器械分类",
    "supplier": "供应商",
    "manufacturer": "生产厂商",
    "purchase_plan": "采购计划",
    "purchase_plan_item": "采购计划明细",
    "purchase_project": "采购项目",
    "purchase_contract": "采购合同",
    "contract_payment": "合同付款记录",
    "medical_device": "医疗设备台账",
    "device_accessory": "设备附属低值品",
    "device_entry": "设备入库单",
    "device_entry_item": "设备入库明细",
    "asset_transfer": "资产流转",
    "inventory_check": "资产盘点单",
    "inventory_check_item": "资产盘点明细",
    "device_scrap": "设备报废",
    "fault_type_dict": "故障类型字典",
    "engineer": "维修工程师",
    "repair_workorder": "维修工单",
    "spare_part": "备件",
    "spare_part_usage": "备件领用",
    "maintenance_template": "保养模板",
    "maintenance_plan": "保养计划",
    "maintenance_record": "保养执行记录",
    "risk_assessment": "风险评估",
    "adverse_event": "不良事件",
    "metrology_record": "计量检定记录",
    "performance_test": "性能检测",
    "maintenance_contract": "维保合同",
    "maintenance_contract_fulfillment": "维保履约记录",
    "maintenance_contract_payment": "维保付款",
    "life_support_device": "生命支持设备",
    "emergency_device_pool": "应急设备池",
    "emergency_device_allocation": "应急设备调配",
    "special_device": "特种设备",
    "leased_device": "租赁设备",
    "device_usage_record": "设备使用记录",
    "device_cost_record": "设备成本记录",
    "device_benefit_summary": "设备效益汇总",
    "sys_config": "系统配置",
    "sys_dict": "数据字典",
    "sys_notification": "系统通知",
    "notification_message": "通知消息（历史）",
    "device_outbound": "设备出库单",
    "device_outbound_item": "设备出库明细",
    "sys_approval_flow": "审批流程定义",
    "sys_approval_node": "审批流程节点",
    "sys_approval_instance": "审批实例",
    "sys_approval_record": "审批操作记录",
    "inspection_plan": "巡检计划",
    "inspection_record": "巡检记录",
    "inspection_record_item": "巡检记录明细",
    "spare_part_transaction": "备件出入库流水",
    "integration_sync_task": "外部系统同步任务",
    "warehouse": "库房",
    "purchase_acceptance": "安装验收",
    "purchase_acceptance_item": "验收清单项",
    "purchase_acceptance_member": "验收小组成员",
    "purchase_bidder": "投标人",
    "purchase_complaint": "质疑投诉",
    "purchase_project_event": "招标过程事件",
    "purchase_alert_snapshot": "采购预警快照",
    "import_template_field": "导入模板字段配置",
    "import_profile_binding": "导入方案绑定",
    "sys_tenant": "租户",
    "sys_tenant_subscription": "租户订阅",
    "platform_user": "平台管理员",
    "sys_menu": "菜单目录",
    "sys_tenant_menu": "租户菜单授权",
    "sys_package": "功能套餐",
    "sys_package_menu": "套餐菜单",
}

COLUMN_HINTS: dict[str, str] = {
    "id": "主键",
    "created_at": "创建时间",
    "updated_at": "更新时间",
    "created_by": "制单人",
    "updated_by": "最后修改人",
    "approved_by": "审核人",
    "approved_at": "审核时间",
    "is_active": "是否启用",
    "remark": "备注",
    "status": "状态",
    "approval_status": "审批状态",
    "dept_id": "所属科室",
    "campus_id": "所属院区",
    "building_id": "所属建筑物",
    "device_id": "关联设备",
    "user_id": "关联用户",
    "supplier_id": "供应商",
    "manufacturer_id": "生产厂商",
    "category_id": "设备分类",
    "contract_id": "采购合同",
    "project_id": "采购项目",
    "plan_id": "采购计划",
    "acceptance_id": "安装验收单",
    "pinyin_code": "拼音简码（检索）",
    "extension_data": "扩展字段JSON（未建模列）",
    "specification": "规格型号",
    "registration_no": "医疗器械注册证号",
    "production_date": "生产日期",
    "service_life_years": "设计使用年限（年）",
    "calibration_period_days": "计量检定周期（天）",
    "last_calibration_date": "上次检定日期",
    "next_calibration_date": "下次检定日期",
    "service_expiry_date": "使用年限到期日",
    "business_chain_no": "采购业务链编号（计划→入库追溯）",
    "version": "乐观锁版本号",
    "plan_type": "计划类型（年度/临时等）",
    "fund_source": "资金来源",
    "use_dept_id": "使用科室",
    "is_imported": "是否进口设备",
    "unit": "计量单位",
    "brand_intent": "意向品牌",
    "is_metrology": "是否计量器具",
    "udi_code": "UDI唯一器械标识",
    "is_large_equipment": "是否大型医用设备",
    "large_equipment_class": "大型设备分类",
    "benefit_analysis_url": "效益分析附件URL",
    "dept_argument_url": "科室论证附件URL",
    "bid_sections": "招标标段说明",
    "bid_evaluation": "评标结果摘要",
    "acceptance_status": "安装验收状态",
    "invoice_summary": "发票汇总说明",
    "plan_code": "计划/巡检编码",
    "last_maintained_at": "上次保养日期",
    "plan_name": "计划名称",
    "start_date": "开始日期",
    "end_date": "结束日期",
    "frequency": "执行频率",
    "record_no": "记录编号",
    "result_summary": "结果摘要",
    "inspect_date": "巡检日期",
    "argument_report_url": "论证报告URL",
    "budget_amount": "预算金额",
    "delivery_deadline": "交货期限",
    "acceptance_report_url": "验收报告URL",
    "paid_amount": "已付金额",
    "payment_progress": "付款进度（%）",
    "finance_auditor_id": "财务审核人",
    "finance_audit_date": "财务审核日期",
    "invoice_type": "发票类型",
    "tax_amount": "税额",
    "voucher_no": "财务凭证号",
    "trace_no": "入库追溯编号",
    "bid_agency": "招标代理机构",
    "notice_date": "招标公告日期",
    "control_price": "招标控制价",
    "contract_type": "合同类型",
    "performance_bond": "履约保证金",
    "registration_cert_url": "注册证附件URL",
    "permissions": "用户权限快照JSON",
    "permission_mode": "权限模式（synced/custom）",
    "tenant_code": "租户编码",
    "tenant_name": "租户名称",
    "schema_name": "数据库Schema名",
    "package_code": "功能套餐编码",
    "credit_code": "统一社会信用代码",
    "menu_code": "菜单编码",
    "parent_code": "父菜单编码",
    "menu_name": "菜单名称",
    "menu_type": "菜单类型",
    "path": "前端路由路径",
    "check_no": "盘点单号",
    "check_name": "盘点名称",
    "total_count": "应盘数量",
    "device_code": "设备编码",
    "device_name": "设备名称",
    "device_status": "设备运行状态",
    "original_value": "原值",
    "net_value": "净值",
    "enable_date": "启用日期",
    "warranty_end_date": "保修截止日期",
    "is_extension": "是否写入extension_data扩展列",
    "target_column": "映射物理列名",
    "profile_code": "导入方案编码",
    "business_type": "业务类型标识",
    "field_key": "字段键",
    "field_label": "字段显示名",
    "field_type": "字段数据类型",
}


def guess_column_comment(table: str, column: str) -> str:
    if column in COLUMN_HINTS:
        return COLUMN_HINTS[column]
    if column.endswith("_id"):
        base = column[:-3]
        return f"关联{TABLE_LABELS.get(base, base)}"
    if column.endswith("_at"):
        return f"{column[:-3]}时间"
    if column.endswith("_date"):
        return f"{column[:-5]}日期"
    if column.endswith("_no"):
        return f"{column[:-3]}编号"
    if column.endswith("_code"):
        return f"{column[:-5]}编码"
    if column.endswith("_name"):
        return f"{column[:-5]}名称"
    if column.startswith("is_"):
        return f"是否{column[3:].replace('_', '')}"
    if column.endswith("_url"):
        return f"{column[:-4]}附件地址"
    if column.endswith("_amount"):
        return f"{column[:-7]}金额"
    if column.endswith("_count"):
        return f"{column[:-6]}数量"
    return column.replace("_", " ")


def parse_alter_columns(v2: str) -> dict[str, list[tuple[str, str]]]:
    alters: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for m in re.finditer(r"ALTER\s+TABLE\s+(\w+)\s+(.*?);", v2, re.DOTALL | re.IGNORECASE):
        if "ADD COLUMN" not in m.group(2).upper():
            continue
        table = m.group(1)
        body = m.group(2)
        for part in re.split(r"ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS\s+", body, flags=re.IGNORECASE)[1:]:
            part = part.strip().rstrip(",")
            cm = re.match(r"(\w+)\s+(.*)", part, re.DOTALL)
            if not cm:
                continue
            col, col_def = cm.group(1), cm.group(2).strip().rstrip(",")
            if col not in [c for c, _ in alters[table]]:
                alters[table].append((col, col_def))
    return alters


def merge_columns(v1: str, alters: dict[str, list[tuple[str, str]]]) -> str:
    for table, columns in alters.items():
        pat = rf"(CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+{table}\s*\()(.*?)(\n\);)"
        m = re.search(pat, v1, re.DOTALL | re.IGNORECASE)
        if not m:
            print(f"  [warn] table not found for merge: {table}")
            continue
        body = m.group(2)
        existing = set(re.findall(r"^\s*(\w+)\s+", body, re.MULTILINE))
        additions = [f"    {c} {d}" for c, d in columns if c not in existing]
        if not additions:
            continue
        new_body = body.rstrip() + ",\n" + ",\n".join(additions)
        v1 = v1[: m.start()] + m.group(1) + new_body + m.group(3) + v1[m.end() :]
    return v1


def strip_v2_alters_and_comments(v2: str) -> str:
    lines: list[str] = []
    skip = False
    for line in v2.splitlines():
        upper = line.upper().strip()
        if upper.startswith("ALTER TABLE") and "ADD COLUMN" in upper:
            skip = True
            continue
        if skip:
            if line.rstrip().endswith(";"):
                skip = False
            continue
        if upper.startswith("COMMENT ON"):
            continue
        lines.append(line)
    out = "\n".join(lines)
    out = re.sub(r"\n{3,}", "\n\n", out).strip() + "\n"
    header = (
        "-- MEIS tenant schema extensions: indexes only (columns merged into V1__tables.sql)\n\n"
    )
    return header + out


def extract_section_table_comment(v1: str, table: str) -> str | None:
    pat = rf"--\s*[\d.]+\s*(.+?)\s*\n\s*CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+{table}\b"
    m = re.search(pat, v1, re.IGNORECASE)
    if m:
        return m.group(1).strip()
    return TABLE_LABELS.get(table)


def parse_create_tables(sql: str) -> dict[str, list[str]]:
    tables: dict[str, list[str]] = {}
    for m in re.finditer(
        r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+(\w+)\s*\((.*?)\n\);",
        sql,
        re.DOTALL | re.IGNORECASE,
    ):
        table = m.group(1)
        body = m.group(2)
        cols: list[str] = []
        for line in body.splitlines():
            line = line.strip()
            if not line or line.startswith("--") or line.startswith("UNIQUE") or line.startswith("PRIMARY"):
                continue
            cm = re.match(r"(\w+)\s+", line)
            if cm:
                cols.append(cm.group(1))
        tables[table] = cols
    return tables


def build_comments(sql: str, schema_label: str) -> str:
    tables = parse_create_tables(sql)
    parts = [
        f"-- MEIS {schema_label} schema: table/column comments for DBAs",
        "",
    ]
    for table, cols in tables.items():
        t_comment = extract_section_table_comment(sql, table) or TABLE_LABELS.get(table, table)
        esc = t_comment.replace("'", "''")
        parts.append(f"COMMENT ON TABLE {table} IS '{esc}';")
        for col in cols:
            if col == "id":
                c_comment = "主键UUID"
            else:
                c_comment = guess_column_comment(table, col)
            esc_c = c_comment.replace("'", "''")
            parts.append(f"COMMENT ON COLUMN {table}.{col} IS '{esc_c}';")
        parts.append("")
    return "\n".join(parts).rstrip() + "\n"


def process_schema(rel_dir: str, schema_label: str) -> None:
    base = ROOT / "meis-tenant" / "src" / "main" / "resources" / "db" / "migrations" / rel_dir
    v1_path = base / "V1__tables.sql"
    v2_path = base / "V2__indexes.sql"
    v4_path = base / "V4__comments.sql"

    v1 = v1_path.read_text(encoding="utf-8")
    v2 = v2_path.read_text(encoding="utf-8")

    alters = parse_alter_columns(v2)
    if alters:
        print(f"[{rel_dir}] merging {sum(len(v) for v in alters.values())} columns into V1")
        v1 = merge_columns(v1, alters)

    v1_header = (
        "-- MEIS tenant business schema: extensions + complete CREATE TABLE (all columns included)\n"
        if rel_dir == "tenant"
        else "-- MEIS platform schema: complete CREATE TABLE (all columns included)\n"
    )
    if not v1.lstrip().startswith("-- MEIS"):
        v1 = v1_header + "\n" + v1
    else:
        v1 = re.sub(r"^-- MEIS consolidated.*?\n", v1_header, v1, count=1)

    v1_path.write_text(v1, encoding="utf-8")

    new_v2 = strip_v2_alters_and_comments(v2)
    v2_path.write_text(new_v2, encoding="utf-8")

    comments = build_comments(v1, schema_label)
    v4_path.write_text(comments, encoding="utf-8")
    print(f"[{rel_dir}] wrote V4__comments.sql ({comments.count(chr(10))} lines)")


def main() -> None:
    process_schema("tenant", "tenant")
    process_schema("public", "public")


if __name__ == "__main__":
    main()
