import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * 离线执行：对所有活跃租户 schema 幂等建表（等同 SchemaTableEnsuring + ShadowGuard 补表）。
 * 用法: java EnsureTenantV1Tables [schema1 schema2 ...]
 */
public class EnsureTenantV1Tables {
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?!IF\\s+NOT\\s+EXISTS)");
    private static final Pattern CREATE_UNIQUE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+UNIQUE\\s+INDEX\\s+(?!IF\\s+NOT\\s+EXISTS)");
    private static final Pattern CREATE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(?!IF\\s+NOT\\s+EXISTS)");

    public static void main(String[] args) throws Exception {
        Path repo = Path.of("d:/sb_project/sb");
        List<String> statements = new ArrayList<>();
        statements.addAll(load(repo.resolve(
                "meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql")));
        statements.addAll(load(repo.resolve(
                "meis-tenant/src/main/resources/db/migrations/tenant/V2__indexes.sql")));

        try (Connection c = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            c.createStatement().execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            c.createStatement().execute("CREATE EXTENSION IF NOT EXISTS \"pgcrypto\"");

            List<String> schemas = args.length > 0 ? List.of(args) : activeTenants(c);
            for (String schema : schemas) {
                System.out.println("=== ensure " + schema + " ===");
                try (Statement st = c.createStatement()) {
                    st.execute("SET search_path TO " + schema + ", public");
                }
                int ok = 0, skip = 0;
                for (String sql : statements) {
                    try (Statement st = c.createStatement()) {
                        st.execute(sql);
                        ok++;
                    } catch (SQLException e) {
                        skip++;
                        System.out.println("SKIP: " + abbrev(sql) + " -> " + e.getMessage());
                    }
                }
                System.out.println("applied=" + ok + " skipped=" + skip);
                reportGaps(c, schema, repo);
            }
        }
    }

    private static void reportGaps(Connection c, String schema, Path repo) throws Exception {
        Set<String> v1 = parseTableNames(repo.resolve(
                "meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql"));
        List<String> missing = new ArrayList<>();
        for (String t : v1) {
            if (!tableExists(c, schema, t)) missing.add(t);
        }
        if (missing.isEmpty()) {
            System.out.println(schema + ": all V1 tables OK (" + v1.size() + ")");
        } else {
            System.out.println(schema + ": STILL MISSING " + missing.size() + " -> " + missing);
        }
    }

    private static List<String> activeTenants(Connection c) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = c.createStatement().executeQuery(
                "SELECT schema_name FROM public.sys_tenant WHERE status='active' ORDER BY 1")) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private static boolean tableExists(Connection c, String schema, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema=? AND table_name=?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Set<String> parseTableNames(Path path) throws Exception {
        Set<String> out = new TreeSet<>();
        Pattern p = Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");
        for (String line : Files.readAllLines(path)) {
            Matcher m = p.matcher(line.trim());
            if (m.find()) out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> load(Path path) throws Exception {
        List<String> raw = splitSql(Files.readString(path, StandardCharsets.UTF_8));
        List<String> out = new ArrayList<>();
        for (String stmt : raw) {
            String t = stmt.trim();
            if (t.isEmpty()) continue;
            String upper = t.toUpperCase(Locale.ROOT);
            if (upper.startsWith("COMMENT ON") || upper.startsWith("ALTER TABLE")) continue;
            String idempotent = toIdempotent(t);
            if (idempotent != null) out.add(idempotent);
        }
        return out;
    }

    private static String toIdempotent(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE TABLE")) {
            return CREATE_TABLE.matcher(sql).replaceFirst("CREATE TABLE IF NOT EXISTS ");
        }
        if (upper.startsWith("CREATE UNIQUE INDEX")) {
            return CREATE_UNIQUE_INDEX.matcher(sql).replaceFirst("CREATE UNIQUE INDEX IF NOT EXISTS ");
        }
        if (upper.startsWith("CREATE INDEX")) {
            return CREATE_INDEX.matcher(sql).replaceFirst("CREATE INDEX IF NOT EXISTS ");
        }
        if (upper.startsWith("CREATE EXTENSION")) return sql;
        return null;
    }

    static List<String> splitSql(String script) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) continue;
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String stmt = current.toString().trim();
                if (stmt.endsWith(";")) stmt = stmt.substring(0, stmt.length() - 1).trim();
                if (!stmt.isEmpty()) list.add(stmt);
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) list.add(tail);
        return list;
    }

    private static String abbrev(String sql) {
        String one = sql.replaceAll("\\s+", " ").trim();
        return one.length() > 80 ? one.substring(0, 80) + "..." : one;
    }
}
