import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class SchemaDump {
    public static void main(String[] args) throws Exception {
        String host = env("POSTGRES_HOST", "localhost");
        String port = env("POSTGRES_PORT", "5432");
        String db = env("POSTGRES_DB", "meis");
        String user = env("POSTGRES_USER", "med");
        String pass = env("POSTGRES_PASSWORD", "med123456");
        String schema = args.length > 0 ? args[0] : "tenant_demo";

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.createStatement().execute("SET search_path TO " + schema);
            dumpSchema(conn, schema);
        }
    }

    private static void dumpSchema(Connection conn, String schema) throws Exception {
        String sql = """
            SELECT table_name, column_name, data_type, udt_name, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name NOT LIKE 'flyway_%'
            ORDER BY table_name, ordinal_position
            """;
        Map<String, List<String>> tables = new TreeMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    String col = rs.getString("column_name");
                    String type = rs.getString("udt_name");
                    if ("varchar".equals(type)) type = "varchar";
                    String nullable = "YES".equals(rs.getString("is_nullable")) ? "" : " NOT NULL";
                    String def = rs.getString("column_default");
                    String defPart = def == null ? "" : " DEFAULT " + def;
                    tables.computeIfAbsent(table, k -> new ArrayList<>())
                            .add(col + "|" + type + nullable + defPart);
                }
            }
        }
        System.out.println("SCHEMA=" + schema);
        System.out.println("TABLE_COUNT=" + tables.size());
        for (var e : tables.entrySet()) {
            System.out.println("TABLE:" + e.getKey());
            for (String c : e.getValue()) System.out.println("  COL:" + c);
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
