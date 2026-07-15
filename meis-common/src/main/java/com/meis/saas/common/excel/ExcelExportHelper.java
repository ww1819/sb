package com.meis.saas.common.excel;

import com.meis.saas.common.persistence.SoftDeleteSupport;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.text.csv.CsvWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class ExcelExportHelper {
    private ExcelExportHelper() {}

    public static void exportCsv(JdbcTemplate jdbc, String table, HttpServletResponse resp) throws IOException {
        exportCsv(jdbc, table, resp, null, null);
    }

    /**
     * @param ids     非空则仅导出这些 id（逗号分隔）；为空则按查询条件导出
     * @param keyword 可选关键词（对常见名称/编码列 ILIKE，无命中列时忽略）
     */
    public static void exportCsv(JdbcTemplate jdbc, String table, HttpServletResponse resp,
                                 String ids, String keyword) throws IOException {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        where.append(SoftDeleteSupport.notDeletedClause(jdbc, table, null));
        List<Object> args = new ArrayList<>();
        if (ids != null && !ids.isBlank()) {
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(5000)
                    .collect(Collectors.toList());
            if (idList.isEmpty()) {
                writeEmpty(resp);
                return;
            }
            where.append(" AND id IN (");
            for (int i = 0; i < idList.size(); i++) {
                if (i > 0) where.append(',');
                where.append("?::uuid");
                args.add(idList.get(i));
            }
            where.append(") ");
        } else if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            // 通用兜底：常见文本列存在则 OR 匹配
            List<String> cols = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
                    String.class, table);
            List<String> candidates = List.of("name", "code", "title", "real_name", "username",
                    "dept_name", "dept_code", "type_name", "type_code", "item_name", "item_code");
            List<String> hit = candidates.stream().filter(cols::contains).toList();
            if (!hit.isEmpty()) {
                where.append(" AND (");
                for (int i = 0; i < hit.size(); i++) {
                    if (i > 0) where.append(" OR ");
                    where.append(hit.get(i)).append("::text ILIKE ?");
                    args.add(kw);
                }
                where.append(") ");
            }
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + where + " LIMIT 5000", args.toArray());
        writeRows(resp, table + ".csv", rows);
    }

    private static void writeEmpty(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/csv;charset=UTF-8");
        resp.getWriter().write("");
    }

    private static void writeRows(HttpServletResponse resp, String filename, List<Map<String, Object>> rows)
            throws IOException {
        if (rows.isEmpty()) {
            writeEmpty(resp);
            return;
        }
        Set<String> headers = new LinkedHashSet<>(rows.get(0).keySet());
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        CsvWriter writer = CsvUtil.getWriter(resp.getWriter());
        writer.write(headers.toArray(new String[0]));
        for (Map<String, Object> row : rows) {
            String[] line = headers.stream().map(h -> row.get(h) != null ? row.get(h).toString() : "").toArray(String[]::new);
            writer.write(line);
        }
        writer.flush();
    }
}
