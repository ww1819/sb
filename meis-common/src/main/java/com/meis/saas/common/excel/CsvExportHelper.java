package com.meis.saas.common.excel;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class CsvExportHelper {
    private CsvExportHelper() {}

    public static void writeRows(HttpServletResponse resp, String filename, String[] headers, List<Map<String, Object>> rows) throws IOException {
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        resp.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        var writer = resp.getWriter();
        writer.write(String.join(",", headers));
        writer.write("\n");
        for (Map<String, Object> row : rows) {
            String[] line = new String[headers.length];
            for (int i = 0; i < headers.length; i++) {
                Object v = row.get(headers[i]);
                line[i] = v == null ? "" : escapeCsv(v.toString());
            }
            writer.write(String.join(",", line));
            writer.write("\n");
        }
        writer.flush();
    }

    private static String escapeCsv(String raw) {
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }
}
