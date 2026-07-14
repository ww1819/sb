import java.sql.*;
public class Q8 {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis","med","med123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT nspname FROM pg_namespace WHERE nspname LIKE 'tenant_%' OR nspname='public' ORDER BY 1")) {
      while (rs.next()) System.out.println(rs.getString(1));
    }
  }
}