package com.meis.saas.asset.controller;

import com.meis.saas.common.audit.OperationLog;
import com.meis.saas.common.excel.ExcelImportHelper;
import com.meis.saas.common.excel.ImportFieldRegistry;
import com.meis.saas.common.excel.ImportProfileService;
import com.meis.saas.common.excel.ImportResult;
import com.meis.saas.common.excel.MedicalDeviceImporter;
import com.meis.saas.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/asset/medical_device")
@RequiredArgsConstructor
public class MedicalDeviceImportController {
    private final JdbcTemplate jdbc;
    private final ImportProfileService importProfileService;

    @GetMapping("/import/template")
    public void importTemplate(@RequestParam(required = false) String profile, HttpServletResponse resp) throws IOException {
        var fields = importProfileService.resolveFields(ImportFieldRegistry.MEDICAL_DEVICE, profile);
        ExcelImportHelper.writeTemplate(resp, "medical_device_import_template.xlsx", fields);
    }

    @GetMapping("/import/fields")
    public Result<List<?>> importFields(@RequestParam(required = false) String profile) {
        return Result.ok(importProfileService.resolveFields(ImportFieldRegistry.MEDICAL_DEVICE, profile));
    }

    @PostMapping("/import")
    @OperationLog(module = "asset", description = "导入设备台账")
    public Result<ImportResult> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String profile) throws IOException {
        var fields = importProfileService.resolveFields(ImportFieldRegistry.MEDICAL_DEVICE, profile);
        var rows = ExcelImportHelper.parseRows(file, fields);
        return Result.ok(MedicalDeviceImporter.importRows(jdbc, rows, fields));
    }
}
