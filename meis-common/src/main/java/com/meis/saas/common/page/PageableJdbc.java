package com.meis.saas.common.page;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PageableJdbc {
    private PageableJdbc() {}

    public static PageResult<Map<String, Object>> query(JdbcTemplate jdbc, String table, PageQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, table, null));
        List<Object> args = new ArrayList<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            where.append(" AND (CAST(id AS TEXT) ILIKE ?) ");
            args.add("%" + q.getKeyword() + "%");
        }
        q.getFilters().forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                where.append(" AND ").append(k).append(" = ? ");
                args.add(v);
            }
        });
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.limit());
        pageArgs.add(q.offset());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + where + " ORDER BY created_at DESC NULLS LAST LIMIT ? OFFSET ?",
                pageArgs.toArray());
        return PageResult.of(rows, total != null ? total : 0, q.getPage(), q.getSize());
    }
}
