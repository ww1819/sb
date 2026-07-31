import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class SchemaCompare {
    static final String MIGRATIONS = "d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant";

    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        Map<String, Set<String>> scriptCols = parseScriptColumns();
        Map<String, Set<String>> dbCols = loadDbColumns(schema);

        Set<String> allTables = new TreeSet<>();
        allTables.addAll(scriptCols.keySet());
        allTables.addAll(dbCols.keySet());

        List<String> dbOnlyTables = new ArrayList<>();
        List<String> scriptOnlyTables = new ArrayList<>();
        List<String> dbOnlyCols = new ArrayList<>();
        List<String> scriptOnlyCols = new ArrayList<>();

        for (String table : allTables) {
            boolean inDb = dbCols.containsKey(table);
            boolean inScript = scriptCols.containsKey(table);
            if (inDb && !inScript) dbOnlyTables.add(table);
            if (inScript && !inDb) scriptOnlyTables.add(table);
            if (inDb && inScript) {
                for (String c : dbCols.get(table)) {
                    if (!scriptCols.get(table).contains(c)) dbOnlyCols.add(table + "." + c);
                }
                for (String c : scriptCols.get(table)) {
                    if (!dbCols.get(table).contains(c)) scriptOnlyCols.add(table + "." + c);
                }
            }
        }

        System.out.println("=== SCHEMA COMPARE: " + schema + " ===");
        System.out.println("\n-- DB has tables, script missing (" + dbOnlyTables.size() + ") --");
        dbOnlyTables.forEach(t -> System.out.println(t));
        System.out.println("\n-- Script has tables, DB missing (" + scriptOnlyTables.size() + ") --");
        scriptOnlyTables.forEach(t -> System.out.println(t));
        System.out.println("\n-- DB has columns, script missing (" + dbOnlyCols.size() + ") --");
        dbOnlyCols.forEach(System.out::println);
        System.out.println("\n-- Script has columns, DB missing (" + scriptOnlyCols.size() + ") --");
        scriptOnlyCols.forEach(System.out::println);
    }

    static Map<String, Set<String>> parseScriptColumns() throws Exception {
        Map<String, Set<String>> result = new TreeMap<>();
        Path dir = Paths.get(MIGRATIONS);
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(Comparator.comparing(p -> versionOf(p.getFileName().toString())))
                    .forEach(files::add);
        }
        Pattern createTable = Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)?\\s+(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);
        Pattern alterAdd = Pattern.compile("ALTER TABLE\\s+(\\w+)\\s+ADD COLUMN(?: IF NOT EXISTS)?\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

        for (Path file : files) {
            String content = Files.readString(file);
            // skip seed-only files for column defs from INSERT
            Matcher cm = createTable.matcher(content);
            while (cm.find()) {
                String table = cm.group(1).toLowerCase();
                int start = cm.end();
                int depth = 1;
                int i = start;
                StringBuilder body = new StringBuilder();
                while (i < content.length() && depth > 0) {
                    char ch = content.charAt(i++);
                    if (ch == '(') depth++;
                    else if (ch == ')') depth--;
                    if (depth > 0) body.append(ch);
                }
                Set<String> cols = result.computeIfAbsent(table, k -> new TreeSet<>());
                for (String line : body.toString().split(",")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--") || line.toUpperCase().startsWith("CONSTRAINT")
                            || line.toUpperCase().startsWith("PRIMARY") || line.toUpperCase().startsWith("UNIQUE")
                            || line.toUpperCase().startsWith("FOREIGN") || line.toUpperCase().startsWith("CHECK")) {
                        continue;
                    }
                    String col = line.split("\\s+")[0].replace("\"", "").toLowerCase();
                    if (!col.isBlank()) cols.add(col);
                }
            }
            Matcher am = alterAdd.matcher(content);
            while (am.find()) {
                String table = am.group(1).toLowerCase();
                String col = am.group(2).toLowerCase();
                result.computeIfAbsent(table, k -> new TreeSet<>()).add(col);
            }
        }
        return result;
    }

    static int versionOf(String name) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : 9999;
    }

    static Map<String, Set<String>> loadDbColumns(String schema) throws Exception {
        Map<String, Set<String>> result = new TreeMap<>();
        String url = "jdbc:postgresql://localhost:5432/meis";
        try (Connection conn = DriverManager.getConnection(url, "med", "med123456")) {
            String sql = """
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name NOT LIKE 'flyway_%'
                ORDER BY table_name, ordinal_position
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
