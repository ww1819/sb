import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** 对比 V1 建表清单与租户 schema 实际表，输出缺表列表。 */
public class V1TenantGapScan {
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");

    public static void main(String[] args) throws Exception {
        Set<String> v1Tables = parseV1Tables(Path.of("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql"));
        System.out.println("V1 tables: " + v1Tables.size());

        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            for (String tenant : List.of("tenant_demo", "tenant_hospdemo03", "tenant_test")) {
                Set<String> tenantTables = tablesInSchema(c, tenant);
                List<String> missing = new ArrayList<>();
                for (String t : v1Tables) {
                    if (!tenantTables.contains(t)) {
                        missing.add(t);
                    }
                }
                System.out.println("\n=== " + tenant + " missing from V1 (" + missing.size() + ") ===");
                for (String m : missing) {
                    boolean publicHas = tableInSchema(c, "public", m);
                    System.out.println("  " + m + (publicHas ? " [PUBLIC SHADOW]" : ""));
                }
            }
        }
    }

    private static Set<String> parseV1Tables(Path path) throws Exception {
        Set<String> out = new TreeSet<>();
        for (String line : Files.readAllLines(path)) {
            String t = line.trim();
            Matcher m = CREATE_TABLE.matcher(t);
            if (m.find()) {
                out.add(m.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return out;
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

    private static boolean tableInSchema(Connection c, String schema, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema=? AND table_name=?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
