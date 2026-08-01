package com.meis.saas.asset.service;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AST-DISP-01：设备处置记录只读聚合。
 */
@Service
@RequiredArgsConstructor
public class DeviceDispositionQueryService {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listByDevice(UUID deviceId) {
        var device = jdbc.queryForList(
                "SELECT 1 FROM medical_device WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, "medical_device", null), deviceId);
        if (device.isEmpty()) throw new BizException(404, "设备不存在");

        String ownNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_ownership_period", "p");
        String scrapNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_scrap", "s");
        String grNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return", "h");
        String griNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_goods_return_item", "i");
        String outNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound", "h");
        String outiNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_outbound_item", "i");
        String retNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_return", "h");
        String retiNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_return_item", "i");
        String entryNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_entry", "h");
        String entryiNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "device_entry_item", "i");
        String xferNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "asset_transfer", "t");
        String chkNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check", "h");
        String chkiNotDel = SoftDeleteSupport.notDeletedClause(jdbc, "inventory_check_item", "i");

        return jdbc.queryForList("""
                SELECT * FROM (
                  SELECT i.id AS line_id,
                         h.id AS biz_id,
                         'entry' AS disposition_type,
                         '入库' AS disposition_type_label,
                         h.entry_no AS biz_no,
                         COALESCE(h.entry_date::timestamptz, h.created_at) AS occurred_at,
                         h.status AS biz_status,
                         'biz_doc' AS source_mode,
                         '业务单据' AS source_mode_label,
                         h.remark AS remark
                  FROM device_entry_item i
                  JOIN device_entry h ON h.id = i.entry_id
                  WHERE i.device_id = ?::uuid
                  """ + entryiNotDel + entryNotDel + """
                    AND h.status = 'completed'

                  UNION ALL
                  SELECT i.id, h.id, 'outbound', '出库', h.outbound_no,
                         COALESCE(h.outbound_date::timestamptz, h.updated_at, h.created_at),
                         COALESCE(h.doc_status, h.status), 'biz_doc', '业务单据', h.purpose
                  FROM device_outbound_item i
                  JOIN device_outbound h ON h.id = i.outbound_id
                  WHERE i.device_id = ?::uuid
                  """ + outiNotDel + outNotDel + """
                    AND COALESCE(h.status, h.doc_status) IN ('issued', 'approved')

                  UNION ALL
                  SELECT i.id, h.id, 'return', '退库', h.return_no,
                         COALESCE(h.return_date::timestamptz, h.updated_at, h.created_at),
                         COALESCE(h.doc_status, h.status), 'biz_doc', '业务单据', h.reason
                  FROM device_return_item i
                  JOIN device_return h ON h.id = i.return_id
                  WHERE i.device_id = ?::uuid
                  """ + retiNotDel + retNotDel + """
                    AND COALESCE(h.status, h.doc_status) IN ('returned', 'approved')

                  UNION ALL
                  SELECT t.id, t.id, 'transfer', '调拨/转仓', t.transfer_no,
                         COALESCE(t.transfer_date::timestamptz, t.updated_at, t.created_at),
                         COALESCE(t.status, t.approval_status), 'biz_doc', '业务单据', t.reason
                  FROM asset_transfer t
                  WHERE t.device_id = ?::uuid
                  """ + xferNotDel + """
                    AND COALESCE(t.status, t.approval_status) IN ('completed', 'approved')

                  UNION ALL
                  SELECT i.id, h.id, 'goods_return', '退货', h.return_no,
                         COALESCE(h.return_date::timestamptz, h.updated_at, h.created_at),
                         COALESCE(h.doc_status, h.status), 'biz_doc', '业务单据', h.reason
                  FROM device_goods_return_item i
                  JOIN device_goods_return h ON h.id = i.return_id
                  WHERE i.device_id = ?::uuid
                  """ + griNotDel + grNotDel + """
                    AND COALESCE(h.status, h.doc_status, h.approval_status) IN ('returned', 'approved')

                  UNION ALL
                  SELECT s.id, s.id, 'scrap', '报废', s.scrap_no,
                         COALESCE(s.scrap_date::timestamptz, s.disposal_date::timestamptz, s.approved_at, s.updated_at),
                         COALESCE(s.status, s.approval_status), 'biz_doc', '业务单据', s.scrap_reason
                  FROM device_scrap s
                  WHERE s.device_id = ?::uuid
                  """ + scrapNotDel + """
                    AND COALESCE(s.status, s.approval_status) IN ('approved', 'completed', 'disposed')

                  UNION ALL
                  SELECT i.id, h.id, 'inventory_loss', '盘亏', h.check_no,
                         COALESCE(i.check_date, h.actual_end_at, h.updated_at, h.created_at),
                         h.status, 'biz_doc', '业务单据', i.remark
                  FROM inventory_check_item i
                  JOIN inventory_check h ON h.id = i.check_id
                  WHERE i.device_id = ?::uuid
                  """ + chkiNotDel + chkNotDel + """
                    AND (i.is_found = FALSE OR i.is_matched = FALSE)

                  UNION ALL
                  SELECT p.id, p.id, 'ownership_transfer', '手工归属变更', p.source_biz_no,
                         p.effective_from, p.confirm_status, COALESCE(p.source_mode, 'manual_transfer'),
                         '手工真变更', p.remark
                  FROM device_ownership_period p
                  WHERE p.device_id = ?::uuid
                    AND p.confirm_status = 'confirmed'
                    AND p.change_reason = 'manual_transfer'
                  """ + ownNotDel + """
                ) u
                ORDER BY occurred_at DESC NULLS LAST
                LIMIT 500
                """,
                deviceId, deviceId, deviceId, deviceId, deviceId, deviceId, deviceId, deviceId);
    }
}
