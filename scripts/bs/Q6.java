import java.sql.*;
public class Q6 {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456");
         Statement s = c.createStatement()) {
      for (String q : new String[]{
        "SELECT count(*) FROM public.sys_config",
        "SELECT config_key FROM public.sys_config",
        "SELECT config_key, is_deleted FROM tenant_demo.sys_config"
      }) {
        System.out.println("--- " + q);
        try (ResultSet rs = s.executeQuery(q)) {
          ResultSetMetaData md = rs.getMetaData();
          while (rs.next()) {
            StringBuilder sb = new StringBuilder();
            for (int i=1;i<=md.getColumnCount();i++) sb.append(rs.getString(i)).append(" | ");
            System.out.println(sb);
          }
        } catch (Exception e) { System.out.println("ERR "+e.getMessage()); }
      }
    }
  }
}