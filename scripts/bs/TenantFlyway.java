import java.sql.*;

public class TenantFlyway {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            String sql = "SELECT version, description, success FROM \"" + schema + "\".flyway_schema_history ORDER BY installed_rank";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getBoolean(3));
                }
            }
        }
    }
}
