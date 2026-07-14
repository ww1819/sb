package com.meis.saas.analytics.controller;

import com.meis.saas.analytics.service.PowerReadingQueryService;
import com.meis.saas.common.page.PageQuery;
import com.meis.saas.common.page.PageResult;
import com.meis.saas.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/power/device")
@RequiredArgsConstructor
public class PowerDeviceReadingController {
    private final PowerReadingQueryService readingQuery;

    @GetMapping("/{deviceId}/readings/page")
    public Result<PageResult<Map<String, Object>>> readingsPage(
            @PathVariable UUID deviceId,
            PageQuery query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate readAtTo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime readAtFromTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime readAtToTime,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        LocalDateTime from = readAtFromTime != null
                ? readAtFromTime
                : (readAtFrom != null ? readAtFrom.atStartOfDay() : null);
        LocalDateTime to = readAtToTime != null
                ? readAtToTime
                : (readAtTo != null ? readAtTo.atTime(LocalTime.MAX) : null);
        return Result.ok(readingQuery.pageByDevice(deviceId, query, from, to, sortOrder));
    }
}
