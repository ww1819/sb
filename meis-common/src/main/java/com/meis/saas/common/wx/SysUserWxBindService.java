package com.meis.saas.common.wx;

import com.meis.saas.common.exception.BizException;
import com.meis.saas.common.ops.OpsClientChannel;
import com.meis.saas.common.persistence.SoftDeleteSupport;
import com.meis.saas.common.persistence.TableColumnCache;
import com.meis.saas.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * WX-BIND-01：用户↔微信 openid 区间绑定（解绑=闭合区间，不物理删）。
 */
@Service
@RequiredArgsConstructor
public class SysUserWxBindService {
    private static final String TABLE = "sys_user_wx_bind";

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listByUser(UUID userId, boolean activeOnly) {
        if (!tableReady()) return List.of();
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM sys_user_wx_bind
                WHERE user_id = ?::uuid
                """);
        sql.append(SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null));
        if (activeOnly) {
            sql.append(" AND effective_to IS NULL ");
        }
        sql.append(" ORDER BY effective_from DESC NULLS LAST, created_at DESC");
        return jdbc.queryForList(sql.toString(), userId);
    }

    /** 当前用户任一活跃绑定的 openid（优先最近一条），供订阅消息快照。 */
    public String resolvePrimaryOpenid(UUID userId) {
        if (!tableReady() || userId == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT wx_openid FROM sys_user_wx_bind
                WHERE user_id = ?::uuid AND effective_to IS NULL
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null) + """
                ORDER BY effective_from DESC NULLS LAST LIMIT 1
                """, userId);
        if (rows.isEmpty() || rows.get(0).get("wx_openid") == null) return null;
        return String.valueOf(rows.get(0).get("wx_openid"));
    }

    /** 写日志用：请求头优先，否则取用户当前活跃绑定。 */
    public String resolveOperatorOpenid(String userId) {
        String fromHeader = com.meis.saas.common.ops.WxOperatorContext.getOpenid();
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader.trim();
        if (userId == null || userId.isBlank()) return null;
        try {
            return resolvePrimaryOpenid(UUID.fromString(userId));
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Map<String, Object> bind(UUID userId, String openid, String unionid, String appId,
                                   String channel, String remark) {
        if (!tableReady()) throw new BizException(500, "缺表 sys_user_wx_bind，请重启 meis-tenant 迁库");
        if (userId == null) throw new BizException(400, "缺少用户");
        if (openid == null || openid.isBlank()) throw new BizException(400, "缺少 wx_openid");
        openid = openid.trim();

        List<Map<String, Object>> same = jdbc.queryForList("""
                SELECT id FROM sys_user_wx_bind
                WHERE user_id = ?::uuid AND wx_openid = ? AND effective_to IS NULL
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), userId, openid);
        if (!same.isEmpty()) {
            syncUserPrimaryOpenid(userId);
            return Map.of("id", same.get(0).get("id"), "bound", true, "already", true);
        }

        List<Map<String, Object>> others = jdbc.queryForList("""
                SELECT id, user_id FROM sys_user_wx_bind
                WHERE wx_openid = ? AND effective_to IS NULL AND user_id <> ?::uuid
                """ + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), openid, userId);
        if (!others.isEmpty()) {
            throw new BizException(400, "该微信账号已绑定其他系统用户，请先在对应用户下解除绑定");
        }

        UUID id = UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("user_id", userId);
        body.put("wx_openid", openid);
        body.put("wx_unionid", blankToNull(unionid));
        body.put("mp_app_id", blankToNull(appId));
        body.put("effective_from", Timestamp.from(Instant.now()));
        body.put("effective_to", null);
        body.put("bind_channel", OpsClientChannel.normalize(channel));
        body.put("remark", blankToNull(remark));
        SoftDeleteSupport.applyInsertAudit(jdbc, TABLE, body);
        jdbc.update("""
                INSERT INTO sys_user_wx_bind (
                  id, user_id, wx_openid, wx_unionid, mp_app_id,
                  effective_from, effective_to, bind_channel, remark,
                  created_by, updated_by, created_by_name, updated_by_name, is_deleted
                ) VALUES (
                  ?::uuid, ?::uuid, ?, ?, ?,
                  ?, NULL, ?, ?,
                  ?::uuid, ?::uuid, ?, ?, COALESCE(?::smallint, 0)
                )
                """,
                body.get("id"), body.get("user_id"), body.get("wx_openid"), body.get("wx_unionid"),
                body.get("mp_app_id"), body.get("effective_from"), body.get("bind_channel"), body.get("remark"),
                body.get("created_by"), body.get("updated_by"),
                body.get("created_by_name"), body.get("updated_by_name"), body.get("is_deleted"));
        syncUserPrimaryOpenid(userId);
        return Map.of("id", id, "bound", true, "already", false, "openidMasked", mask(openid));
    }

    @Transactional
    public Map<String, Object> unbind(UUID bindId, String channel) {
        if (!tableReady()) throw new BizException(500, "缺表 sys_user_wx_bind");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM sys_user_wx_bind WHERE id = ?::uuid"
                        + SoftDeleteSupport.notDeletedClause(jdbc, TABLE, null), bindId);
        if (rows.isEmpty()) throw new BizException(404, "绑定记录不存在");
        Map<String, Object> row = rows.get(0);
        if (row.get("effective_to") != null) {
            return Map.of("id", bindId, "unbound", true, "already", true);
        }
        UUID userId = UUID.fromString(String.valueOf(row.get("user_id")));
        Instant now = Instant.now();
        String actor = TenantContext.getUserId();
        String actorName = SoftDeleteSupport.resolveUserDisplayName(jdbc, actor);
        jdbc.update("""
                UPDATE sys_user_wx_bind SET
                  effective_to = ?, unbound_at = ?, unbound_by = ?::uuid, unbound_by_name = ?,
                  unbind_channel = ?, updated_at = NOW()
                WHERE id = ?::uuid
                """, Timestamp.from(now), Timestamp.from(now), actor, actorName,
                OpsClientChannel.normalize(channel), bindId);
        syncUserPrimaryOpenid(userId);
        return Map.of("id", bindId, "unbound", true, "already", false);
    }

    /** 解绑该用户下全部当前有效绑定。 */
    @Transactional
    public int unbindAllActive(UUID userId, String channel) {
        List<Map<String, Object>> active = listByUser(userId, true);
        int n = 0;
        for (Map<String, Object> r : active) {
            unbind(UUID.fromString(String.valueOf(r.get("id"))), channel);
            n++;
        }
        return n;
    }

    private void syncUserPrimaryOpenid(UUID userId) {
        if (!TableColumnCache.hasColumn(jdbc, "sys_user", "wx_openid")) return;
        String primary = resolvePrimaryOpenid(userId);
        jdbc.update("UPDATE sys_user SET wx_openid = ?, updated_at = NOW() WHERE id = ?::uuid",
                primary, userId);
    }

    private boolean tableReady() {
        try {
            return TableColumnCache.columns(jdbc, TABLE).contains("wx_openid");
        } catch (Exception e) {
            return false;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String mask(String openid) {
        if (openid == null || openid.length() < 6) return "";
        return openid.substring(0, 3) + "***" + openid.substring(openid.length() - 3);
    }
}
