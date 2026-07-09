import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class FullSchemaCompare {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        Path v1 = Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant/V1__tables.sql");
        Map<String, Set<String>> v1Cols = parseCreateTables(Files.readString(v1));
        Map<String, Set<String>> patchCols = parseAlters(Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant"));
        for (var e : patchCols.entrySet()) v1Cols.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).addAll(e.getValue());
        Map<String, Set<String>> dbCols = loadDb(schema);

        Set<String> tables = new TreeSet<>();
        tables.addAll(v1Cols.keySet());
        tables.addAll(dbCols.keySet());

        List<String> dbOnlyTables = new ArrayList<>();
        List<String> scriptOnlyTables = new ArrayList<>();
        List<String> dbOnlyCols = new ArrayList<>();
        List<String> scriptOnlyCols = new ArrayList<>();

        for (String t : tables) {
            boolean inDb = dbCols.containsKey(t);
            boolean inSc = v1Cols.containsKey(t);
            if (inDb && !inSc) dbOnlyTables.add(t);
            if (inSc && !inDb) scriptOnlyTables.add(t);
            if (inDb && inSc) {
                for (String c : dbCols.get(t)) if (!v1Cols.get(t).contains(c)) dbOnlyCols.add(t + "." + c);
                for (String c : v1Cols.get(t)) if (!dbCols.get(t).contains(c)) scriptOnlyCols.add(t + "." + c);
            }
        }

        System.out.println("DB_ONLY_TABLES=" + dbOnlyTables.size());
        dbOnlyTables.forEach(t -> System.out.println("DB_TABLE:" + t));
        System.out.println("SCRIPT_ONLY_TABLES=" + scriptOnlyTables.size());
        scriptOnlyTables.forEach(t -> System.out.println("SCRIPT_TABLE:" + t));
        System.out.println("DB_ONLY_COLS=" + dbOnlyCols.size());
        dbOnlyCols.forEach(c -> System.out.println("DB_COL:" + c));
        System.out.println("SCRIPT_ONLY_COLS=" + scriptOnlyCols.size());
        scriptOnlyCols.forEach(c -> System.out.println("SCRIPT_COL:" + c));
    }

    static Map<String, Set<String>> parseCreateTables(String sql) {
        Map<String, Set<String>> result = new TreeMap<>();
        Pattern p = Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)?\\s+(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        while (m.find()) {
            String table = m.group(1).toLowerCase();
            int start = m.end();
            int depth = 1, i = start;
            StringBuilder body = new StringBuilder();
            while (i < sql.length() && depth > 0) {
                char ch = sql.charAt(i++);
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                if (depth > 0) body.append(ch);
            }
            Set<String> cols = new TreeSet<>();
            for (String raw : body.toString().split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;
                line = line.replaceAll("\\s*--.*$", "").trim();
                if (line.endsWith(",")) line = line.substring(0, line.length() - 1).trim();
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("CONSTRAINT") || upper.startsWith("PRIMARY KEY")
                        || upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY")
                        || upper.startsWith("CHECK") || upper.startsWith("EXCLUDE")) continue;
                Matcher cm = Pattern.compile("^\"?(\\w+)\"?\\s+").matcher(line);
                if (cm.find()) cols.add(cm.group(1).toLowerCase());
            }
            result.put(table, cols);
        }
        return result;
    }

    static Map<String, Set<String>> parseAlters(Path dir) throws Exception {
        Map<String, Set<String>> result = new TreeMap<>();
        Pattern p = Pattern.compile("ALTER TABLE\\s+(\\w+)\\s+ADD COLUMN(?: IF NOT EXISTS)?\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        try (var stream = Files.list(dir)) {
            for (Path f : stream.filter(x -> x.getFileName().toString().matches("V\\d+__.*\\.sql")).toList()) {
                String content = Files.readString(f);
                Matcher m = p.matcher(content);
                while (m.find()) {
                    result.computeIfAbsent(m.group(1).toLowerCase(), k -> new TreeSet<>()).add(m.group(2).toLowerCase());
                }
            }
        }
        return result;
    }

    static Map<String, Set<String>> loadDb(String schema) throws Exception {
        Map<String, Set<String>> result = new TreeMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = ? AND table_name NOT LIKE 'flyway_%'
                ORDER BY 1, 2
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
