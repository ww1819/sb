import java.sql.*;
import java.util.*;

/** 扫描租户 schema 缺表但 public 有表的串写风险。 */
public class SchemaShadowScan {
    public static void main(String[] args) throws Exception {
        String[] tenants = {"tenant_demo", "tenant_hospdemo03", "tenant_test"};
        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            Set<String> publicTables = tablesInSchema(c, "public");
            System.out.println("=== public tables (" + publicTables.size() + ") ===");
            for (String t : publicTables) {
                if (t.startsWith("power_") || t.startsWith("flyway")) {
                    System.out.println("  " + t);
                }
            }

            for (String tenant : tenants) {
                Set<String> tenantTables = tablesInSchema(c, tenant);
                List<String> shadowRisk = new ArrayList<>();
                for (String t : publicTables) {
                    if (t.startsWith("flyway") || t.startsWith("sys_tenant")) continue;
                    if (!tenantTables.contains(t) && isTenantBusinessTable(t)) {
                        shadowRisk.add(t);
                    }
                }
                System.out.println("\n=== " + tenant + " missing but public has (" + shadowRisk.size() + ") ===");
                for (String t : shadowRisk) {
                    System.out.println("  RISK: " + t);
                }
            }

            // V1 power module tables check
            String[] powerTables = {
                    "power_base_station", "power_tag", "power_device_status",
                    "power_monitor_record", "power_current_reading", "power_tag_bind_log"
            };
            System.out.println("\n=== power module per tenant ===");
            for (String tenant : tenants) {
                c.createStatement().execute("SET search_path TO " + tenant);
                for (String t : powerTables) {
                    try (ResultSet rs = c.createStatement().executeQuery(
                            "SELECT to_regclass('" + t + "')")) {
                        rs.next();
                        System.out.println(tenant + " " + t + " = " + rs.getString(1));
                    }
                }
            }
        }
    }

    private static boolean isTenantBusinessTable(String name) {
        if (name.startsWith("pg_")) return false;
        return switch (name) {
            case "sys_tenant", "sys_tenant_user", "sys_platform_user" -> false;
            default -> !name.startsWith("flyway");
        };
    }

    private static Set<String> tablesInSchema(Connection c, String schema) throws SQLException {
        Set<String> out = new TreeSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE'")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }
}
