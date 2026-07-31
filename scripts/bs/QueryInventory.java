import java.sql.*;

public class QueryInventory {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            for (String schema : new String[]{"tenant_demo", "tenant_hospdemo03", "tenant_test"}) {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET search_path TO " + schema);
                    try (ResultSet rs = st.executeQuery(
                            "SELECT COUNT(*) AS c FROM inventory_check")) {
                        rs.next();
                        System.out.println(schema + " count=" + rs.getInt(1));
                    }
                    try (ResultSet rs = st.executeQuery(
                            "SELECT id, check_no, check_name, dept_id, audit_status, created_at FROM inventory_check ORDER BY created_at DESC LIMIT 5")) {
                        while (rs.next()) {
                            System.out.printf("  %s | %s | %s | dept=%s | audit=%s%n",
                                    rs.getString("check_no"), rs.getString("id"),
                                    rs.getString("check_name"), rs.getString("dept_id"),
                                    rs.getString("audit_status"));
                        }
                    }
                }
            }
        }
    }
}
