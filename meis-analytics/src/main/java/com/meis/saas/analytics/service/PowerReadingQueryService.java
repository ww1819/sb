package com.meis.saas.analytics.service;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PowerReadingQueryService {
    private final JdbcTemplate jdbc;

    public PageResult<Map<String, Object>> pageByStation(UUID stationId, PageQuery query,
            LocalDateTime readAtFrom, LocalDateTime readAtTo, String sortOrder) {
        return pageInternal("r.station_id = ?::uuid", List.of(stationId), query, readAtFrom, readAtTo, sortOrder);
    }

    public PageResult<Map<String, Object>> pageByTag(UUID tagId, PageQuery query,
            LocalDateTime readAtFrom, LocalDateTime readAtTo, String sortOrder) {
        return pageInternal("r.tag_id = ?::uuid", List.of(tagId), query, readAtFrom, readAtTo, sortOrder);
    }

    public PageResult<Map<String, Object>> pageByDevice(UUID deviceId, PageQuery query,
            LocalDateTime readAtFrom, LocalDateTime readAtTo, String sortOrder) {
        return pageInternal("r.device_id = ?::uuid", List.of(deviceId), query, readAtFrom, readAtTo, sortOrder);
    }

    private PageResult<Map<String, Object>> pageInternal(String scopeSql, List<Object> scopeArgs, PageQuery query,
            LocalDateTime readAtFrom, LocalDateTime readAtTo, String sortOrder) {
        StringBuilder where = new StringBuilder(" WHERE ").append(scopeSql).append(' ');
        List<Object> args = new ArrayList<>(scopeArgs);
        if (readAtFrom != null) {
            where.append(" AND r.read_at >= ? ");
            args.add(readAtFrom);
        }
        if (readAtTo != null) {
            where.append(" AND r.read_at <= ? ");
            args.add(readAtTo);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (r.tag_code ILIKE ? OR r.station_code ILIKE ? OR r.device_code ILIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM power_current_reading r" + where, Long.class, args.toArray());
        String order = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        int offset = (query.getPage() - 1) * query.getSize();
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        var rows = jdbc.queryForList("""
                SELECT r.id, r.tag_id, r.tag_code, r.station_id, r.station_code,
                       COALESCE(s.station_name, r.station_code) AS station_name,
                       r.device_id, r.device_code, r.current_ma, r.read_at, r.created_at
                FROM power_current_reading r
                LEFT JOIN power_base_station s ON s.id = r.station_id
                """ + where + " ORDER BY r.read_at " + order + " LIMIT ? OFFSET ?", pageArgs.toArray());
        return new PageResult<>(rows, total, query.getPage(), query.getSize());
    }

    public List<Map<String, Object>> listByTagForExport(UUID tagId, LocalDateTime readAtFrom,
            LocalDateTime readAtTo, String sortOrder) {
        StringBuilder where = new StringBuilder(" WHERE r.tag_id = ?::uuid ");
        List<Object> args = new ArrayList<>();
        args.add(tagId);
        if (readAtFrom != null) {
            where.append(" AND r.read_at >= ? ");
            args.add(readAtFrom);
        }
        if (readAtTo != null) {
            where.append(" AND r.read_at <= ? ");
            args.add(readAtTo);
        }
        String order = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        return jdbc.queryForList("""
                SELECT r.tag_code, r.station_code, r.device_id, r.device_code, r.current_ma, r.read_at, r.created_at
                FROM power_current_reading r
                """ + where + " ORDER BY r.read_at " + order + " LIMIT 50000", args.toArray());
    }
}
