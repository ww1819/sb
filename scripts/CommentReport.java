import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Report missing comments after backfill; optionally list counts. */
public class CommentReport {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            int noTable = 0, noCol = 0, totalTables = 0;
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT c.relname, obj_description(c.oid)
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('r','v') AND c.relname NOT LIKE 'flyway_%'
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        totalTables++;
                        if (rs.getString(2) == null || rs.getString(2).isBlank()) noTable++;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT col_description(
                    (quote_ident(table_schema)||'.'||quote_ident(table_name))::regclass::oid,
                    ordinal_position)
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name NOT LIKE 'flyway_%'
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String d = rs.getString(1);
                        if (d == null || d.isBlank()) noCol++;
                    }
                }
            }
            System.out.printf("schema=%s tables=%d tables_no_comment=%d columns_no_comment=%d%n",
                    schema, totalTables, noTable, noCol);
        }
    }
}
