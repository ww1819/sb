import java.sql.*;
public class Q5 {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT config_key, category_code FROM tenant_demo.sys_config ORDER BY created_at DESC NULLS LAST LIMIT 10")) {
      while (rs.next()) System.out.println(rs.getString(1) + " | " + rs.getString(2));
    }
  }
}