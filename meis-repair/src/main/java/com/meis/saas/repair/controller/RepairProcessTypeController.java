package com.meis.saas.repair.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.repair.service.RepairWorkorderSegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/repair/process-type")
@RequiredArgsConstructor
public class RepairProcessTypeController {
    private final RepairWorkorderSegmentService segmentService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(segmentService.listActiveTypes());
    }

    @GetMapping("/addable")
    public Result<List<Map<String, Object>>> addable(@RequestParam UUID workorderId,
                                                     @RequestParam String status) {
        return Result.ok(segmentService.listEngineerAddableTypes(workorderId, status));
    }
}
