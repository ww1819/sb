package com.meis.saas.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meis.saas.system.config.OpsHealthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class ServiceHealthProbeService {
    private final OpsHealthProperties props;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Map<String, Object> checkAll() {
        List<OpsHealthProperties.HealthTarget> targets = props.getHealthTargets();
        if (targets == null || targets.isEmpty()) {
            targets = List.of();
        }
        int timeoutMs = Math.max(500, props.getTimeoutMs());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, Math.max(1, targets.size())));
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (OpsHealthProperties.HealthTarget t : targets) {
                futures.add(CompletableFuture.supplyAsync(() -> probeOne(t, timeoutMs), pool));
            }
            List<Map<String, Object>> items = futures.stream().map(CompletableFuture::join).toList();
            long up = items.stream().filter(i -> "UP".equals(i.get("status"))).count();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("checkedAt", Instant.now().toString());
            out.put("total", items.size());
            out.put("up", up);
            out.put("down", items.size() - up);
            out.put("items", items);
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private Map<String, Object> probeOne(OpsHealthProperties.HealthTarget t, int timeoutMs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", t.getName());
        row.put("url", t.getUrl());
        long start = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(t.getUrl()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            long latency = (System.nanoTime() - start) / 1_000_000L;
            row.put("latencyMs", latency);
            row.put("httpStatus", res.statusCode());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                String status = parseActuatorStatus(res.body());
                if ("UP".equals(status)) {
                    row.put("status", "UP");
                    row.put("message", "ok");
                } else {
                    row.put("status", "DOWN");
                    row.put("message", "actuator status=" + status);
                }
            } else {
                row.put("status", "DOWN");
                row.put("message", "HTTP " + res.statusCode());
            }
        } catch (Exception e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            row.put("latencyMs", latency);
            row.put("status", "DOWN");
            row.put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return row;
    }

    private String parseActuatorStatus(String body) {
        if (body == null || body.isBlank()) return "UNKNOWN";
        try {
            JsonNode n = objectMapper.readTree(body);
            if (n.has("status")) return n.get("status").asText("UNKNOWN");
        } catch (Exception ignored) {
            if (body.contains("\"status\":\"UP\"") || body.contains("\"status\": \"UP\"")) return "UP";
        }
        return "UNKNOWN";
    }
}
