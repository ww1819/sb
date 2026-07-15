package com.meis.saas.common.excel;

import com.meis.saas.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExcelImportHelper {
    private ExcelImportHelper() {}

    public static List<Map<String, String>> parseRows(MultipartFile file) throws IOException {
        return parseRows(file, null);
    }

    public static List<Map<String, String>> parseRows(MultipartFile file, List<ImportFieldDef> fields) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择 Excel 或 CSV 文件");
        }
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return CsvImportHelper.parseCsvRows(file, fields);
        }
        if (name != null && !name.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)$")) {
            throw new BizException(400, "仅支持 .xlsx / .xls / .csv 格式");
        }
        return parseExcelRows(file.getInputStream(), fields);
    }

    static List<Map<String, String>> parseExcelRows(InputStream in, List<ImportFieldDef> fields) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) throw new BizException(400, "Excel 无工作表");
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) throw new BizException(400, "Excel 无表头行");
            Row headerRow = it.next();
            List<String> rawHeaders = readRow(headerRow);

            // 无表头两列表：首行即为「编码 / 名称」数据
            boolean dataFirst = isCategoryDataFirstRow(rawHeaders, fields);
            List<String> headers;
            List<Map<String, String>> rows = new ArrayList<>();
            if (dataFirst) {
                headers = List.of("category_code", "category_name");
                rows.add(rowMap(headers, rawHeaders));
            } else {
                headers = fields == null || fields.isEmpty()
                        ? ImportHeaderMapper.normalizeHeaders(rawHeaders, List.of())
                        : ImportHeaderMapper.normalizeHeaders(rawHeaders, fields);
            }

            while (it.hasNext()) {
                Row row = it.next();
                List<String> cells = readRow(row);
                if (cells.stream().allMatch(v -> v == null || v.isBlank())) continue;
                rows.add(rowMap(headers, cells));
            }
            if (rows.isEmpty()) throw new BizException(400, "Excel 无有效数据");
            return rows;
        }
    }

    /**
     * 设备分类：解析后若没有 category_code 列，则把前两列当作编码/名称。
     */
    public static List<Map<String, String>> ensureCategoryTwoColumnRows(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) return rows;
        boolean hasCode = rows.stream().anyMatch(r ->
                nonBlank(r.get("category_code")) || nonBlank(r.get("分类编码")) || nonBlank(r.get("分类编码(68码)")));
        if (hasCode) return rows;
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> r : rows) {
            List<String> vals = new ArrayList<>();
            for (String v : r.values()) {
                if (v != null && !v.isBlank()) vals.add(v.trim());
            }
            if (vals.size() < 2) continue;
            Map<String, String> m = new LinkedHashMap<>();
            m.put("category_code", vals.get(0));
            m.put("category_name", vals.get(1));
            out.add(m);
        }
        return out;
    }

    private static boolean isCategoryDataFirstRow(List<String> rawHeaders, List<ImportFieldDef> fields) {
        if (rawHeaders == null || rawHeaders.size() < 2) return false;
        boolean hasCategoryFields = fields != null && fields.stream().anyMatch(f -> "category_code".equals(f.getFieldKey()));
        if (!hasCategoryFields) return false;
        String first = rawHeaders.get(0) == null ? "" : rawHeaders.get(0).trim();
        String second = rawHeaders.get(1) == null ? "" : rawHeaders.get(1).trim();
        if (second.isBlank()) return false;
        List<String> normalized = ImportHeaderMapper.normalizeHeaders(rawHeaders, fields);
        if (normalized.stream().anyMatch("category_code"::equals)) return false;
        String digits = first.replaceAll("\\.0$", "");
        return digits.matches("\\d{1,6}");
    }

    private static Map<String, String> rowMap(List<String> headers, List<String> cells) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int c = 0; c < headers.size(); c++) {
            String key = headers.get(c);
            if (key == null || key.isBlank()) continue;
            String val = c < cells.size() && cells.get(c) != null ? cells.get(c).trim() : "";
            map.put(key, val);
        }
        return map;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    public static void writeTemplate(HttpServletResponse resp, String filename, List<ImportFieldDef> fields) throws IOException {
        if (fields == null || fields.isEmpty()) {
            throw new BizException(400, "无可用导入字段");
        }
        List<ImportFieldDef> sorted = fields.stream()
                .sorted(Comparator.comparingInt(ImportFieldDef::getSortOrder))
                .toList();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet dataSheet = workbook.createSheet("导入数据");
            Sheet metaSheet = workbook.createSheet("字段说明");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = dataSheet.createRow(0);
            for (int i = 0; i < sorted.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(sorted.get(i).getFieldLabel());
                cell.setCellStyle(headerStyle);
                dataSheet.setColumnWidth(i, Math.min(6000, sorted.get(i).getFieldLabel().length() * 512 + 2048));
            }

            Row metaHeader = metaSheet.createRow(0);
            String[] metaCols = {"字段名称", "字段标识", "必填", "类型", "存储", "说明"};
            for (int i = 0; i < metaCols.length; i++) {
                Cell cell = metaHeader.createCell(i);
                cell.setCellValue(metaCols[i]);
                cell.setCellStyle(headerStyle);
            }
            for (int i = 0; i < sorted.size(); i++) {
                ImportFieldDef f = sorted.get(i);
                Row row = metaSheet.createRow(i + 1);
                row.createCell(0).setCellValue(f.getFieldLabel());
                row.createCell(1).setCellValue(f.getFieldKey());
                row.createCell(2).setCellValue(f.isRequired() ? "是" : "否");
                row.createCell(3).setCellValue(f.getFieldType());
                row.createCell(4).setCellValue(f.isExtension() ? "扩展(JSON)" : "标准列");
                row.createCell(5).setCellValue(f.getRemark() != null ? f.getRemark() : "");
            }

            if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                filename = filename.replaceAll("\\.(csv|xls)$", "") + ".xlsx";
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            byte[] bytes = bos.toByteArray();

            resp.reset();
            resp.setBufferSize(bytes.length);
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            resp.setContentLength(bytes.length);
            resp.getOutputStream().write(bytes);
            resp.getOutputStream().flush();
        }
    }

    private static List<String> readRow(Row row) {
        List<String> cells = new ArrayList<>();
        if (row == null) return cells;
        int last = row.getLastCellNum();
        if (last < 0) return cells;
        for (int i = 0; i < last; i++) {
            cells.add(readCell(row.getCell(i)));
        }
        return cells;
    }

    private static final DataFormatter CELL_FORMATTER = new DataFormatter();

    private static String readCell(Cell cell) {
        if (cell == null) return "";
        String formatted = CELL_FORMATTER.formatCellValue(cell);
        if (formatted != null && !formatted.isBlank()) return formatted.trim();
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : trimNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> CELL_FORMATTER.formatCellValue(cell);
            default -> "";
        };
    }

    private static String trimNumeric(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
