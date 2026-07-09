import java.sql.*;
import java.util.*;

public class HasComments {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT c.relname, obj_description(c.oid)
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind IN ('r','v') AND obj_description(c.oid) IS NOT NULL
                ORDER BY 1
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) System.out.println("HAS_TABLE:" + rs.getString(1) + " => " + rs.getString(2));
                }
            }
        }
    }
}
