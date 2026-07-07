package com.meis.saas.purchase.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.purchase.support.PurchaseChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/purchase/trace")
@RequiredArgsConstructor
public class PurchaseTraceController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public Result<Map<String, Object>> trace(@RequestParam String keyword) {
        return Result.ok(PurchaseChainService.trace(jdbc, keyword));
    }
}
