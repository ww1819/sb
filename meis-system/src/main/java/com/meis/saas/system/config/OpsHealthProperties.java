package com.meis.saas.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "meis.ops")
public class OpsHealthProperties {
    /** 单目标 HTTP 超时（毫秒） */
    private int timeoutMs = 2500;

    private List<HealthTarget> healthTargets = defaultTargets();

    @Data
    public static class HealthTarget {
        private String name;
        private String url;
    }

    private static List<HealthTarget> defaultTargets() {
        List<HealthTarget> list = new ArrayList<>();
        add(list, "meis-gateway", "http://127.0.0.1:8080/actuator/health");
        add(list, "meis-auth", "http://127.0.0.1:8081/actuator/health");
        add(list, "meis-tenant", "http://127.0.0.1:8082/actuator/health");
        add(list, "meis-system", "http://127.0.0.1:8083/actuator/health");
        add(list, "meis-purchase", "http://127.0.0.1:8084/actuator/health");
        add(list, "meis-asset", "http://127.0.0.1:8085/actuator/health");
        add(list, "meis-repair", "http://127.0.0.1:8086/actuator/health");
        add(list, "meis-maintain", "http://127.0.0.1:8087/actuator/health");
        add(list, "meis-qc", "http://127.0.0.1:8088/actuator/health");
        add(list, "meis-maintenance-contract", "http://127.0.0.1:8089/actuator/health");
        add(list, "meis-special", "http://127.0.0.1:8090/actuator/health");
        add(list, "meis-analytics", "http://127.0.0.1:8091/actuator/health");
        add(list, "meis-file", "http://127.0.0.1:8092/actuator/health");
        add(list, "meis-notification", "http://127.0.0.1:8093/actuator/health");
        add(list, "meis-integration", "http://127.0.0.1:8094/actuator/health");
        return list;
    }

    private static void add(List<HealthTarget> list, String name, String url) {
        HealthTarget t = new HealthTarget();
        t.setName(name);
        t.setUrl(url);
        list.add(t);
    }
}
