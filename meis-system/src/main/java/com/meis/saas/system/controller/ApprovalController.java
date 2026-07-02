package com.meis.saas.system.controller;

import com.meis.saas.common.result.Result;
import com.meis.saas.common.workflow.ApprovalFlowService;
import com.meis.saas.common.workflow.ApprovalInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system/approval")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalFlowService flowService;
    private final ApprovalInstanceService instanceService;

    @GetMapping("/flows")
    public Result<List<Map<String, Object>>> flows() {
        return Result.ok(flowService.listFlows());
    }

    @GetMapping("/flows/{flowId}/nodes")
    public Result<List<Map<String, Object>>> nodes(@PathVariable UUID flowId) {
        return Result.ok(flowService.listNodes(flowId));
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return Result.ok(instanceService.submit(
                body.get("businessType").toString(),
                UUID.fromString(body.get("businessId").toString()),
                body.getOrDefault("businessNo", "").toString(),
                body.getOrDefault("title", "").toString(),
                UUID.fromString(body.get("applicantId").toString()),
                ((Number) body.getOrDefault("amount", 0)).doubleValue()));
    }

    @PostMapping("/{instanceId}/approve")
    public Result<Map<String, Object>> approve(@PathVariable UUID instanceId, @RequestBody Map<String, Object> body) {
        return Result.ok(instanceService.approve(instanceId,
                UUID.fromString(body.get("approverId").toString()),
                body.getOrDefault("comment", "").toString()));
    }

    @PostMapping("/{instanceId}/reject")
    public Result<Map<String, Object>> reject(@PathVariable UUID instanceId, @RequestBody Map<String, Object> body) {
        return Result.ok(instanceService.reject(instanceId,
                UUID.fromString(body.get("approverId").toString()),
                body.getOrDefault("comment", "").toString()));
    }

    @GetMapping("/instance/{instanceId}/records")
    public Result<List<Map<String, Object>>> records(@PathVariable UUID instanceId) {
        return Result.ok(instanceService.records(instanceId));
    }

    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending(@RequestParam List<String> roles) {
        return Result.ok(instanceService.pendingForUser(null, roles));
    }

    @PostMapping("/{instanceId}/withdraw")
    public Result<Map<String, Object>> withdraw(@PathVariable UUID instanceId, @RequestBody Map<String, Object> body) {
        return Result.ok(instanceService.withdraw(instanceId, UUID.fromString(body.get("applicantId").toString())));
    }

    @GetMapping("/business")
    public Result<Map<String, Object>> byBusiness(@RequestParam String businessType, @RequestParam UUID businessId) {
        return Result.ok(instanceService.getByBusiness(businessType, businessId));
    }
}
