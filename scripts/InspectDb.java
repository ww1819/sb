import java.sql.*;

public class InspectDb {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://" + args[0] + ":" + args[1] + "/" + args[2];
        try (Connection conn = DriverManager.getConnection(url, args[3], args[4]);
             Statement st = conn.createStatement()) {
            System.out.println("=== schemas ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT nspname FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND nspname<>'information_schema' ORDER BY 1")) {
                while (rs.next()) System.out.println(rs.getString(1));
            }
            System.out.println("=== sys_user cols ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT table_schema, column_name, data_type, column_default, is_nullable "
                            + "FROM information_schema.columns WHERE table_name='sys_user' "
                            + "AND column_name IN ('is_repair_engineer','is_deleted') "
                            + "ORDER BY table_schema, column_name")) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3)
                            + " | default=" + rs.getString(4) + " | null=" + rs.getString(5));
                }
            }
        }
    }
}
