package com.meis.saas.purchase.support;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

public final class PurchaseChainService {
    private PurchaseChainService() {}

    public static String newChainNo(String planCode) {
        return "BC-" + planCode;
    }

    public static String resolvePlanChainNo(JdbcTemplate jdbc, Object planId) {
        if (planId == null) return null;
        var rows = jdbc.queryForList(
                "SELECT business_chain_no, plan_code FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), planId);
        if (rows.isEmpty()) return null;
        Object chain = rows.get(0).get("business_chain_no");
        if (chain != null && !chain.toString().isBlank()) return chain.toString();
        return newChainNo(rows.get(0).get("plan_code").toString());
    }

    public static void ensurePlanChain(JdbcTemplate jdbc, UUID planId, String planCode) {
        jdbc.update("""
            UPDATE purchase_plan SET business_chain_no = COALESCE(business_chain_no, ?)
            WHERE id = ?::uuid
            """, newChainNo(planCode), planId);
    }

    public static Map<String, Object> trace(JdbcTemplate jdbc, String keyword) {
        if (keyword == null || keyword.isBlank()) return Map.of("keyword", "", "chains", List.of());
        String kw = "%" + keyword.trim() + "%";
        var plans = jdbc.queryForList("""
            SELECT id, plan_code, business_chain_no, approval_status, total_budget, created_at
            FROM purchase_plan
            WHERE (plan_code ILIKE ? OR business_chain_no ILIKE ? OR CAST(id AS TEXT) ILIKE ?)
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null) + """
            ORDER BY created_at DESC LIMIT 5
            """, kw, kw, kw);
        List<Map<String, Object>> chains = new ArrayList<>();
        for (Map<String, Object> plan : plans) {
            chains.add(buildChain(jdbc, plan));
        }
        if (chains.isEmpty()) {
            var byContract = jdbc.queryForList(
                    "SELECT id FROM purchase_contract WHERE (contract_code ILIKE ? OR business_chain_no ILIKE ?)"
                            + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null) + " LIMIT 1",
                    kw, kw);
            if (!byContract.isEmpty()) {
                chains.add(buildChainByContract(jdbc, UUID.fromString(byContract.get(0).get("id").toString())));
            }
        }
        return Map.of("keyword", keyword, "chains", chains);
    }

    private static Map<String, Object> buildChain(JdbcTemplate jdbc, Map<String, Object> plan) {
        UUID planId = UUID.fromString(plan.get("id").toString());
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("plan", plan);
        chain.put("items", jdbc.queryForList(
                "SELECT * FROM purchase_plan_item WHERE plan_id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan_item", null), planId));
        chain.put("projects", jdbc.queryForList("""
            SELECT pj.*, s.supplier_name FROM purchase_project pj
            LEFT JOIN supplier s ON s.id = pj.supplier_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
            WHERE pj.plan_id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj"), planId));
        var projects = (List<Map<String, Object>>) chain.get("projects");
        List<Map<String, Object>> contracts = new ArrayList<>();
        List<Map<String, Object>> acceptances = new ArrayList<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (Map<String, Object> pj : projects) {
            UUID projectId = UUID.fromString(pj.get("id").toString());
            var pcs = jdbc.queryForList("""
                SELECT pc.*, s.supplier_name FROM purchase_contract pc
                LEFT JOIN supplier s ON s.id = pc.supplier_id
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
                WHERE pc.project_id = ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc"), projectId);
            contracts.addAll(pcs);
            for (Map<String, Object> pc : pcs) {
                UUID contractId = UUID.fromString(pc.get("id").toString());
                acceptances.addAll(jdbc.queryForList(
                        "SELECT * FROM purchase_acceptance WHERE contract_id = ?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_acceptance", null), contractId));
                entries.addAll(jdbc.queryForList("""
                    SELECT e.*, s.supplier_name FROM device_entry e
                    LEFT JOIN supplier s ON s.id = e.supplier_id
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "supplier", "s") + """
                    WHERE e.contract_id = ?::uuid
                    """ + SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "e"), contractId));
                devices.addAll(jdbc.queryForList(
                        "SELECT device_code, device_name, purchase_price, device_status FROM medical_device WHERE contract_id = ?::uuid"
                                + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null),
                        contractId));
            }
        }
        chain.put("contracts", contracts);
        chain.put("acceptances", acceptances);
        chain.put("entries", entries);
        chain.put("devices", devices);
        chain.put("payments", contracts.isEmpty() ? List.of() : jdbc.queryForList("""
            SELECT cp.* FROM contract_payment cp
            WHERE cp.contract_id IN (SELECT id FROM purchase_contract WHERE project_id IN (
              SELECT id FROM purchase_project WHERE plan_id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", null) + ")"
                + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", null) + ")"
                + SoftDeleteSupport.notDeletedClause(jdbc, "contract_payment", "cp") + """
            ORDER BY cp.created_at
            """, planId));
        return chain;
    }

    private static Map<String, Object> buildChainByContract(JdbcTemplate jdbc, UUID contractId) {
        var pc = jdbc.queryForList("""
            SELECT pc.*, pj.plan_id FROM purchase_contract pc
            LEFT JOIN purchase_project pj ON pj.id = pc.project_id
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_project", "pj") + """
            WHERE pc.id = ?::uuid
            """ + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_contract", "pc"), contractId);
        if (pc.isEmpty()) return Map.of();
        Object planId = pc.get(0).get("plan_id");
        if (planId == null) return Map.of("contract", pc.get(0));
        var plan = jdbc.queryForList(
                "SELECT * FROM purchase_plan WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "purchase_plan", null), planId);
        return plan.isEmpty() ? Map.of("contract", pc.get(0)) : buildChain(jdbc, plan.get(0));
    }
}
