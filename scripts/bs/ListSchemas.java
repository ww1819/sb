import java.sql.*;

public class ListSchemas {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/meis";
        try (Connection conn = DriverManager.getConnection(url, "med", "med123456")) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT schema_name FROM information_schema.schemata
                     WHERE schema_name IN ('public') OR schema_name LIKE 'tenant%'
                     ORDER BY schema_name
                     """)) {
                while (rs.next()) System.out.println(rs.getString(1));
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("""
                     SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank
                     """)) {
                System.out.println("--- public flyway ---");
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getBoolean(3));
                }
            }
        }
    }
}
