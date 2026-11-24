import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * 离线结构补全：活跃租户 schema 幂等建表/索引 + 执行 R__ 结构脚本（等同启动迁库的结构部分）。
 * <p>
 * 阶段：V1 建表 → V2 索引 → R__columns_audit → R__columns_biz。
 * 不跑 R__data_fix（字典/回填仍由 meis-tenant Flyway 负责）。
 * <p>
 * 用法:
 *   java EnsureTenantV1Tables [schema ...]
 * 环境变量（可选）: MEIS_ROOT, POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
 */
public class EnsureTenantV1Tables {
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?!IF\\s+NOT\\s+EXISTS)");
    private static final Pattern CREATE_UNIQUE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+UNIQUE\\s+INDEX\\s+(?!IF\\s+NOT\\s+EXISTS)");
    private static final Pattern CREATE_INDEX =
            Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(?!IF\\s+NOT\\s+EXISTS)");

    public static void main(String[] args) throws Exception {
        Path repo = resolveRepoRoot();
        String host = env("POSTGRES_HOST", "localhost");
        String port = env("POSTGRES_PORT", "5432");
        String db = firstNonBlank(System.getenv("POSTGRES_DB"), System.getenv("POSTGRES_DATABASE"), "meis");
        String user = firstNonBlank(System.getenv("POSTGRES_USER"), System.getenv("DB_USER"), "med");
        String pass = firstNonBlank(System.getenv("POSTGRES_PASSWORD"), System.getenv("DB_PASSWORD"), "med123456");
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        List<String> structureStmts = new ArrayList<>();
        structureStmts.addAll(loadCreateStatements(repo.resolve(
                "meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql")));
        structureStmts.addAll(loadCreateStatements(repo.resolve(
                "meis-tenant/src/main/resources/db/migrations/tenant/V2__indexes.sql")));

        List<Path> rScripts = List.of(
                repo.resolve("meis-tenant/src/main/resources/db/migrations/tenant/R__columns_audit.sql"),
                repo.resolve("meis-tenant/src/main/resources/db/migrations/tenant/R__columns_biz.sql")
        );

        System.out.println("Repo: " + repo.toAbsolutePath());
        System.out.println("DB: " + url + " user=" + user);

        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                st.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
                st.execute("CREATE EXTENSION IF NOT EXISTS \"pgcrypto\"");
            }

            List<String> schemas = args.length > 0 ? List.of(args) : activeTenants(c);
            if (schemas.isEmpty()) {
                System.out.println("No active tenants / schemas to ensure.");
                return;
            }
            for (String schema : schemas) {
                System.out.println("=== ensure " + schema + " ===");
                try (Statement st = c.createStatement()) {
                    st.execute("SET search_path TO " + schema + ", public");
                }
                int[] v12 = applyAll(c, structureStmts, true);
                System.out.println("  V1/V2: applied=" + v12[0] + " skipped=" + v12[1]);

                for (Path r : rScripts) {
                    if (!Files.exists(r)) {
                        System.out.println("  WARN missing " + r.getFileName());
                        continue;
                    }
                    List<String> stmts = loadRepeatableStatements(r);
                    int[] cnt = applyAll(c, stmts, true);
                    System.out.println("  " + r.getFileName() + ": applied=" + cnt[0]
                            + " skipped=" + cnt[1] + " (stmts=" + stmts.size() + ")");
                }
                reportGaps(c, schema, repo);
            }
        }
        System.out.println("Schema ensure done.");
    }

    private static Path resolveRepoRoot() {
        String envRoot = System.getenv("MEIS_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            Path p = Path.of(envRoot);
            if (Files.isDirectory(p.resolve("meis-tenant"))) return p.toAbsolutePath().normalize();
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("meis-tenant"))
                    && Files.isDirectory(p.resolve("scripts"))) {
                return p;
            }
        }
        // scripts/bs → repo root
        Path fromBs = cwd.getFileName() != null && "bs".equalsIgnoreCase(cwd.getFileName().toString())
                ? cwd.getParent() != null && cwd.getParent().getParent() != null
                ? cwd.getParent().getParent() : cwd
                : cwd;
        if (Files.isDirectory(fromBs.resolve("meis-tenant"))) return fromBs;
        throw new IllegalStateException("Cannot locate repo root (meis-tenant). Set MEIS_ROOT. cwd=" + cwd);
    }

    private static int[] applyAll(Connection c, List<String> statements, boolean continueOnError)
            throws SQLException {
        int ok = 0, skip = 0;
        for (String sql : statements) {
            try (Statement st = c.createStatement()) {
                st.execute(sql);
                ok++;
            } catch (SQLException e) {
                skip++;
                if (!continueOnError) throw e;
                System.out.println("  SKIP: " + abbrev(sql) + " -> " + e.getMessage());
            }
        }
        return new int[]{ok, skip};
    }

    private static void reportGaps(Connection c, String schema, Path repo) throws Exception {
        Set<String> v1 = parseTableNames(repo.resolve(
                "meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql"));
        List<String> missing = new ArrayList<>();
        for (String t : v1) {
            if (!tableExists(c, schema, t)) missing.add(t);
        }
        if (missing.isEmpty()) {
            System.out.println("  " + schema + ": all V1 tables OK (" + v1.size() + ")");
        } else {
            System.out.println("  " + schema + ": STILL MISSING " + missing.size() + " -> " + missing);
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
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            Matcher m = p.matcher(line.trim());
            if (m.find()) out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** V1/V2：仅 CREATE TABLE/INDEX（幂等化），跳过 ALTER/COMMENT。 */
    private static List<String> loadCreateStatements(Path path) throws Exception {
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

    /** R__：执行全部语句（ALTER / CREATE INDEX / DO / UPDATE…），含美元引用体。 */
    private static List<String> loadRepeatableStatements(Path path) throws Exception {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        sql = sql.replaceAll("(?m)^\\s*--.*$", "");
        List<String> out = new ArrayList<>();
        for (String stmt : splitSqlAware(sql)) {
            String t = stmt.trim();
            if (t.isEmpty()) continue;
            String upper = t.toUpperCase(Locale.ROOT);
            if (upper.startsWith("COMMENT ON")) continue;
            out.add(t);
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

    /** 简单按行 ';' 分割（V1/V2 无复杂美元引用体）。 */
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

    /** 支持 $tag$...$tag$ 与单引号（R__ DO 块）。 */
    static List<String> splitSqlAware(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        String dollarTag = null;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (dollarTag != null) {
                if (sql.startsWith(dollarTag, i)) {
                    cur.append(dollarTag);
                    i += dollarTag.length() - 1;
                    dollarTag = null;
                } else {
                    cur.append(ch);
                }
                continue;
            }
            if (!inSingle && ch == '$') {
                int end = findDollarTagEnd(sql, i);
                if (end > i) {
                    dollarTag = sql.substring(i, end + 1);
                    cur.append(dollarTag);
                    i = end;
                    continue;
                }
            }
            if (ch == '\'') {
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    cur.append("''");
                    i++;
                    continue;
                }
                inSingle = !inSingle;
            }
            if (ch == ';' && !inSingle) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur = new StringBuilder();
                continue;
            }
            cur.append(ch);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    static int findDollarTagEnd(String sql, int i) {
        int j = i + 1;
        while (j < sql.length()) {
            char c = sql.charAt(j);
            if (c == '$') return j;
            if (!(Character.isLetterOrDigit(c) || c == '_')) return -1;
            j++;
        }
        return -1;
    }

    private static String abbrev(String sql) {
        String one = sql.replaceAll("\\s+", " ").trim();
        return one.length() > 80 ? one.substring(0, 80) + "..." : one;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
