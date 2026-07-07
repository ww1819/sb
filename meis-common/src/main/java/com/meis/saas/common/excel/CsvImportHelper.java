package com.meis.saas.common.excel;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvUtil;
import com.meis.saas.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** @deprecated 请使用 {@link ExcelImportHelper}；保留 CSV 解析兼容。 */
public final class CsvImportHelper {
    private CsvImportHelper() {}

    public static List<Map<String, String>> parseRows(MultipartFile file) throws IOException {
        return ExcelImportHelper.parseRows(file);
    }

    static List<Map<String, String>> parseCsvRows(MultipartFile file, List<ImportFieldDef> fields) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择 CSV 文件");
        }
        CsvReader reader = CsvUtil.getReader();
        CsvData data = reader.read(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        if (data.getRowCount() < 2) {
            throw new BizException(400, "CSV 无数据行");
        }
        List<String> headers = fields == null || fields.isEmpty()
                ? ImportHeaderMapper.normalizeHeaders(data.getRow(0).getRawList(), List.of())
                : ImportHeaderMapper.normalizeHeaders(data.getRow(0).getRawList(), fields);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < data.getRowCount(); i++) {
            List<String> cells = data.getRow(i).getRawList();
            if (cells.stream().allMatch(v -> v == null || v.isBlank())) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c);
                if (key.isBlank()) continue;
                row.put(key, c < cells.size() && cells.get(c) != null ? cells.get(c).trim() : "");
            }
            rows.add(row);
        }
        if (rows.isEmpty()) throw new BizException(400, "CSV 无有效数据");
        return rows;
    }

    /** @deprecated 使用 {@link ExcelImportHelper#writeTemplate} */
    public static void writeTemplate(HttpServletResponse resp, String filename, String[] headers) throws IOException {
        List<ImportFieldDef> fields = new ArrayList<>();
        int order = 10;
        for (String h : headers) {
            fields.add(ImportFieldDef.builder().fieldKey(h).fieldLabel(h).fieldType("string")
                    .targetColumn(h).sortOrder(order).build());
            order += 10;
        }
        ExcelImportHelper.writeTemplate(resp, filename.replace(".csv", ".xlsx"), fields);
    }

    public static Boolean parseBoolean(String raw) { return ImportValueParser.parseBoolean(raw); }
    public static Integer parseInteger(String raw) { return ImportValueParser.parseInteger(raw); }
    public static Double parseDouble(String raw) { return ImportValueParser.parseDouble(raw); }
    public static String require(String raw, String label) { return ImportValueParser.require(raw, label); }
}
