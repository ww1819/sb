package com.meis.saas.purchase.controller;

import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import com.meis.saas.purchase.support.PurchasePageQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** PUR-UI-14：招标管理（议价通过明细） */
@RestController
@RequestMapping("/api/purchase/bidding")
@RequiredArgsConstructor
public class PurchaseBiddingController {
    private final JdbcTemplate jdbc;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(PageQuery query) {
        return Result.ok(PurchasePageQueries.bargainPassedPlanItemPage(jdbc, query));
    }
}
