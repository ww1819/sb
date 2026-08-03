package com.meis.saas.common.page;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private long total;
    private int page;
    private int size;
    /** 可选：查询结果汇总（如金额合计），不影响既有四字段构造 */
    private Map<String, Object> aggregates;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }

    public PageResult<T> withAggregates(Map<String, Object> aggregates) {
        this.aggregates = aggregates;
        return this;
    }
}
