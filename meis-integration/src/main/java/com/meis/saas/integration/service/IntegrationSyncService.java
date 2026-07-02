package com.meis.saas.integration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationSyncService {
    private final JdbcTemplate jdbc;

    public Map<String, Object> runSync(String systemCode, Map<String, Object> payload) {
        UUID taskId = UUID.randomUUID();
        String status = "completed";
        try {
            jdbc.update("INSERT INTO integration_sync_task (id, system_code, task_type, status, payload) VALUES (?::uuid,?,?,?::jsonb,?)",
                    taskId, systemCode.toUpperCase(), payload.getOrDefault("taskType", "manual"), status,
                    toJson(payload));
        } catch (Exception e) {
            status = "failed";
            jdbc.update("INSERT INTO integration_sync_task (id, system_code, task_type, status, result) VALUES (?::uuid,?,?,?::jsonb,?)",
                    taskId, systemCode.toUpperCase(), payload.getOrDefault("taskType", "manual"), status, e.getMessage());
        }
        return Map.of("taskId", taskId.toString(), "system", systemCode, "status", status);
    }

    private String toJson(Map<String, Object> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }
}
