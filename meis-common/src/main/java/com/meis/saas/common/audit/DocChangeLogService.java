package com.meis.saas.common.audit;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * 业务单据事件与明细字段变更（附录 OPS.6 / 约定包 §6.7），与 {@link EntityChangeLogService} 分离。
 */
@Service
@RequiredArgsConstructor
public class DocChangeLogService {
    private final JdbcTemplate jdbc;

    public void event(String module, String docType, UUID docId, String docNo,
                      String eventType, String client, String remark) {
        insert(module, docType, docId, docNo, eventType, null, null, null, null, null, client, remark);
    }

    public void fieldChange(String module, String docType, UUID docId, String docNo,
                            String entityType, UUID entityId, String field,
                            Object oldValue, Object newValue, String client) {
        if (Objects.equals(stringify(oldValue), stringify(newValue))) return;
        insert(module, docType, docId, docNo, "field_change", entityType, entityId,
                field, stringify(oldValue), stringify(newValue), client, null);
    }

    private void insert(String module, String docType, UUID docId, String docNo, String eventType,
                        String entityType, UUID entityId, String field,
                        String oldValue, String newValue, String client, String remark) {
        String userId = TenantContext.getUserId();
        String name = SoftDeleteSupport.resolveUserDisplayName(jdbc, userId);
        if ((name == null || name.isBlank()) && TenantContext.get() != null) {
            name = TenantContext.get().getUsername();
        }
        jdbc.update("""
                INSERT INTO sys_doc_change_log
                (id, module, doc_type, doc_id, doc_no, event_type, entity_type, entity_id,
                 field_name, old_value, new_value, client, operator_id, operator_name, remark)
                VALUES (?::uuid,?,?,?::uuid,?,?,?,?,?,?,?,?,?::uuid,?,?)
                """, UUID.randomUUID(), module, docType, docId, docNo, eventType, entityType, entityId,
                field, oldValue, newValue, client != null ? client : "web", userId, name, remark);
    }

    private static String stringify(Object v) {
        return v == null ? null : v.toString();
    }
}
