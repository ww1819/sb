import java.sql.*;
public class Q4 {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456");
         Statement s = c.createStatement()) {
      ResultSet rs = s.executeQuery("SELECT tableoid::regclass::text AS tbl, config_key FROM public.sys_config WHERE config_key LIKE 'test_old_only%' UNION ALL SELECT 'tenant_demo.sys_config', config_key FROM tenant_demo.sys_config WHERE config_key LIKE 'test_old_only%' OR config_key='k_test_x1'");
      while (rs.next()) System.out.println(rs.getString(1) + " -> " + rs.getString(2));
    }
  }
}