import java.sql.*;
import java.util.*;

public class MissingComments {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {

            List<String> noTableComment = new ArrayList<>();
            List<String> noColumnComment = new ArrayList<>();

            Map<String, String> tableComments = new TreeMap<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT c.relname, obj_description(c.oid)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('r','v') AND c.relname NOT LIKE 'flyway_%'
                ORDER BY c.relname
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String t = rs.getString(1);
                        String desc = rs.getString(2);
                        tableComments.put(t, desc);
                        if (desc == null || desc.isBlank()) noTableComment.add(t);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT table_name, column_name, col_description(
                    (quote_ident(table_schema)||'.'||quote_ident(table_name))::regclass::oid,
                    ordinal_position)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name NOT LIKE 'flyway_%'
                ORDER BY table_name, ordinal_position
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String t = rs.getString(1);
                        String c = rs.getString(2);
                        String desc = rs.getString(3);
                        if (desc == null || desc.isBlank()) {
                            noColumnComment.add(t + "." + c);
                        }
                    }
                }
            }

            System.out.println("SCHEMA=" + schema);
            System.out.println("TABLES_TOTAL=" + tableComments.size());
            System.out.println("TABLES_NO_COMMENT=" + noTableComment.size());
            for (String t : noTableComment) System.out.println("NO_TABLE:" + t);
            System.out.println("COLUMNS_NO_COMMENT=" + noColumnComment.size());
            for (String c : noColumnComment) System.out.println("NO_COL:" + c);
        }
    }
}
