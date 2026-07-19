package com.meis.saas.purchase.support;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PurchaseValidators {
    private PurchaseValidators() {}

    public static void validateProjectAmount(JdbcTemplate jdbc, Object planId, Object totalAmount) {
        if (planId == null || totalAmount == null) return;
        var plan = jdbc.queryForList(
                "SELECT total_budget FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), planId);
        if (plan.isEmpty()) return;
        double budget = toDouble(plan.get(0).get("total_budget"));
        double amount = toDouble(totalAmount);
        if (budget > 0 && amount > budget * 1.05) {
            throw new BizException(400, "项目金额不能超过计划预算的105%");
        }
    }

    public static void validateContractAmount(JdbcTemplate jdbc, Object projectId, Object contractAmount, UUID contractId) {
        if (projectId == null || contractAmount == null) return;
        var project = jdbc.queryForList(
                "SELECT total_amount FROM purchase_project WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null), projectId);
        if (project.isEmpty()) return;
        double projectAmount = toDouble(project.get(0).get("total_amount"));
        double amount = toDouble(contractAmount);
        if (projectAmount > 0 && amount > projectAmount * 1.02) {
            throw new BizException(400, "合同金额不能超过项目定标金额");
        }
        if (contractId != null) {
            var dup = jdbc.queryForList(
                    "SELECT id FROM purchase_contract WHERE project_id = ?::uuid AND id != ?::uuid"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null) + " LIMIT 1",
                    projectId, contractId);
            if (!dup.isEmpty()) throw new BizException(400, "该项目已存在其他合同");
        }
    }

    public static void validatePaymentTotal(JdbcTemplate jdbc, UUID contractId, List<Map<String, Object>> payments) {
        var contract = jdbc.queryForList(
                "SELECT contract_amount FROM purchase_contract WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
        if (contract.isEmpty()) return;
        double contractAmount = toDouble(contract.get(0).get("contract_amount"));
        double sum = payments.stream().mapToDouble(p -> toDouble(p.get("payment_amount"))).sum();
        if (contractAmount > 0 && sum > contractAmount * 1.001) {
            throw new BizException(400, "付款计划合计不能超过合同金额");
        }
    }

    /** PUR-UI-23：支付比例合计不得超过 100% */
    public static void validatePaymentRatioTotal(List<Map<String, Object>> payments) {
        if (payments == null || payments.isEmpty()) return;
        boolean anyRatio = false;
        double sum = 0;
        for (Map<String, Object> p : payments) {
            double ratio = toDouble(p.get("payment_ratio"));
            if (ratio > 0) {
                anyRatio = true;
                sum += ratio;
            }
        }
        if (anyRatio && sum > 100.001) {
            throw new BizException(400, "支付比例合计不能超过100%");
        }
    }

    public static void recalcContractPaymentProgress(JdbcTemplate jdbc, UUID contractId) {
        var contract = jdbc.queryForList(
                "SELECT contract_amount FROM purchase_contract WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
        if (contract.isEmpty()) return;
        double contractAmount = toDouble(contract.get(0).get("contract_amount"));
        var paid = jdbc.queryForList("""
            SELECT COALESCE(SUM(payment_amount), 0) AS paid
            FROM contract_payment WHERE contract_id = ?::uuid AND status = 'paid'
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "contract_payment", null), contractId);
        double paidAmount = toDouble(paid.get(0).get("paid"));
        double progress = contractAmount > 0 ? Math.min(100, paidAmount / contractAmount * 100) : 0;
        jdbc.update("""
            UPDATE purchase_contract SET paid_amount = ?, payment_progress = ?, updated_at = NOW()
            WHERE id = ?::uuid
            """, paidAmount, progress, contractId);
        var project = jdbc.queryForList(
                "SELECT project_id FROM purchase_contract WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null), contractId);
        if (!project.isEmpty() && project.get(0).get("project_id") != null) {
            jdbc.update("""
                UPDATE purchase_project SET updated_at = NOW() WHERE id = ?::uuid
                """, project.get(0).get("project_id"));
        }
    }

    public static String buildTraceNo(JdbcTemplate jdbc, UUID contractId) {
        var chain = jdbc.queryForList("""
            SELECT pc.business_chain_no, pl.plan_code, pj.project_code, pc.contract_code
            FROM purchase_contract pc
            LEFT JOIN purchase_project pj ON pj.id = pc.project_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj") + """
            LEFT JOIN purchase_plan pl ON pl.id = pj.plan_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", "pl") + """
            WHERE pc.id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc"), contractId);
        if (chain.isEmpty()) return null;
        Map<String, Object> row = chain.get(0);
        Object bc = row.get("business_chain_no");
        if (bc != null && !bc.toString().isBlank()) return bc.toString();
        return String.join(" / ",
                str(row.get("plan_code")),
                str(row.get("project_code")),
                str(row.get("contract_code")));
    }

    public static void checkVersion(JdbcTemplate jdbc, String table, UUID id, Object clientVersion) {
        if (clientVersion == null) return;
        var rows = jdbc.queryForList("SELECT version FROM " + table + " WHERE id = ?::uuid"
                + SoftDeleteSupport.notDeletedClause(jdbc, table, null), id);
        if (rows.isEmpty()) return;
        int dbVer = rows.get(0).get("version") instanceof Number n ? n.intValue() : 1;
        int reqVer = clientVersion instanceof Number n ? n.intValue() : Integer.parseInt(clientVersion.toString());
        if (reqVer != dbVer) {
            throw new BizException(409, "数据已被他人修改，请刷新后重试");
        }
    }

    public static void bumpVersion(JdbcTemplate jdbc, String table, UUID id) {
        jdbc.update("UPDATE " + table + " SET version = COALESCE(version, 1) + 1, updated_at = NOW() WHERE id = ?::uuid", id);
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(Object v) {
        return v != null ? v.toString() : "-";
    }
}
