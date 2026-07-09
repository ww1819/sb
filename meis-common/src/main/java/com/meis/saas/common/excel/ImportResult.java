package com.meis.saas.common.excel;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult {
    private int successCount;
    private int failCount;
    private List<String> errors = new ArrayList<>();

    public void addError(int rowNum, String message) {
        errors.add("第" + rowNum + "行: " + message);
        failCount++;
    }

    public void addSuccess() {
        successCount++;
    }
}
