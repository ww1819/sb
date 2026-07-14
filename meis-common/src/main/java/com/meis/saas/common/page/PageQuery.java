package com.meis.saas.common.page;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class PageQuery {
    private int page = 1;
    private int size = 20;
    private String keyword;
    private Map<String, String> filters = new HashMap<>();

    private String sortBy;
    private String sortOrder;

    public int offset() {
        return Math.max(0, (page - 1) * size);
    }

    public int limit() {
        return Math.min(Math.max(size, 1), 500);
    }
}
