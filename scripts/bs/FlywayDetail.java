import java.sql.*;

public class FlywayDetail {
    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            String sql = "SELECT installed_rank, version, description, script, checksum, success FROM \"" + schema + "\".flyway_schema_history ORDER BY installed_rank";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.printf("%s | v%s | %s | %s | checksum=%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
                }
            }
        }
    }
}
