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

public final class ExcelExportHelper {
    private ExcelExportHelper() {}

    public static void exportCsv(JdbcTemplate jdbc, String table, HttpServletResponse resp) throws IOException {
        String where = " WHERE 1=1 " + SoftDeleteSupport.notDeletedClause(jdbc, table, null);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table + where + " LIMIT 5000");
        if (rows.isEmpty()) {
            resp.setContentType("text/csv;charset=UTF-8");
            resp.getWriter().write("");
            return;
        }
        Set<String> headers = new LinkedHashSet<>(rows.get(0).keySet());
        String filename = table + ".csv";
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
