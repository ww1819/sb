import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class PatchGenerator {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        Path v1 = Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql");
        Map<String, Map<String, String>> script = parseColumnsWithDefs(Files.readString(v1));
        Path v5 = Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant/V5__inventory_check_audit.sql");
        mergeAlters(script, Files.readString(v5));
        Map<String, Set<String>> db = loadDb(schema);

        List<String> patch = new ArrayList<>();
        patch.add("-- Auto-generated: columns in script but missing in DB schema " + schema);
        patch.add("-- Safe to re-run: ADD COLUMN IF NOT EXISTS");
        patch.add("");

        for (var te : script.entrySet()) {
            String table = te.getKey();
            if (!db.containsKey(table)) continue;
            for (var ce : te.getValue().entrySet()) {
                String col = ce.getKey();
                if (!db.get(table).contains(col)) {
                    patch.add("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + col + " " + ce.getValue() + ";");
                }
            }
        }

        List<String> dbOnly = new ArrayList<>();
        for (var te : db.entrySet()) {
            String table = te.getKey();
            if (!script.containsKey(table)) {
                if (!table.startsWith("v_")) dbOnly.add("-- legacy table in DB only: " + table);
                continue;
            }
            for (String col : te.getValue()) {
                if (!script.get(table).containsKey(col)) {
                    dbOnly.add("-- DB column not in V1 script: " + table + "." + col);
                }
            }
        }

        Path out = Paths.get("d:/sb_project/sb/db/source/patches/tenant_column_patches.sql");
        Files.createDirectories(out.getParent());
        List<String> all = new ArrayList<>();
        all.add("-- =============================================================================");
        all.add("-- MEIS tenant column patches (idempotent)");
        all.add("-- Bring existing business schemas in sync with V1__tables.sql + extensions");
        all.add("-- Execute: scripts/apply-tenant-patches.ps1 -Schema <schema>");
        all.add("-- =============================================================================");
        all.add("");
        all.addAll(patch);
        if (!dbOnly.isEmpty()) {
            all.add("");
            all.add("-- Notes: DB objects not modeled in current create script");
            all.addAll(dbOnly);
        }
        Files.write(out, all);
        System.out.println("Wrote " + patch.size() + " patch statements to " + out);
        System.out.println("DB-only notes: " + dbOnly.size());
    }

    static Map<String, Map<String, String>> parseColumnsWithDefs(String sql) {
        Map<String, Map<String, String>> result = new TreeMap<>();
        Pattern p = Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)?\\s+(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase();
            int start = m.end(), depth = 1, i = start;
            StringBuilder body = new StringBuilder();
            while (i < sql.length() && depth > 0) {
                char ch = sql.charAt(i++);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                if (depth > 0) body.append(ch);
            }
            Map<String, String> cols = new TreeMap<>();
            for (String part : splitColumnDefs(body.toString())) {
                String line = part.trim().replaceAll("\\s*--.*$", "").trim();
                if (line.isEmpty()) continue;
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("CONSTRAINT") || upper.startsWith("PRIMARY KEY")
                        || upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY")
                        || upper.startsWith("CHECK") || upper.startsWith("EXCLUDE")) continue;
                Matcher cm = Pattern.compile("^\"?(\\w+)\"?\\s+(.*)$", Pattern.CASE_INSENSITIVE).matcher(line);
                if (!cm.find()) continue;
                String col = cm.group(1).toLowerCase();
                String def = normalizeDef(cm.group(2).trim());
                cols.put(col, def);
            }
            result.put(table, cols);
        }
        return result;
    }

    static List<String> splitColumnDefs(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'' && (i == 0 || body.charAt(i - 1) != '\\')) inQuote = !inQuote;
            if (!inQuote) {
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                else if (ch == ',' && depth == 0) {
                    parts.add(cur.toString());
                    cur = new StringBuilder();
                    continue;
                }
            }
            cur.append(ch);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

    static String normalizeDef(String def) {
        def = def.replaceAll("(?i)REFERENCES\\s+\\w+\\([^)]*\\)(\\s+ON\\s+DELETE\\s+\\w+)?", "").trim();
        if (def.endsWith(",")) def = def.substring(0, def.length() - 1).trim();
        return def;
    }

    static void mergeAlters(Map<String, Map<String, String>> script, String sql) {
        Pattern p = Pattern.compile(
                "ALTER TABLE\\s+(\\w+)\\s+ADD COLUMN(?: IF NOT EXISTS)?\\s+(\\w+)\\s+([^;]+);",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        while (m.find()) {
            script.computeIfAbsent(m.group(1).toLowerCase(), k -> new TreeMap<>())
                    .put(m.group(2).toLowerCase(), m.group(3).trim());
        }
    }

    static Map<String, Set<String>> loadDb(String schema) throws Exception {
        Map<String, Set<String>> result = new TreeMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = ? AND table_name NOT LIKE 'flyway_%'
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getString(1).toLowerCase(), k -> new TreeSet<>())
                                .add(rs.getString(2).toLowerCase());
                    }
                }
            }
        }
        return result;
    }
}
