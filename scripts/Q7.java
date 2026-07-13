import java.sql.*;
public class Q7 {
  public static void main(String[] a) throws Exception {
    String url = a[0];
    try (Connection c = DriverManager.getConnection(url,"med","med123456");
         Statement s = c.createStatement()) {
      System.out.println("URL="+url);
      try (ResultSet rs = s.executeQuery("SELECT current_database(), inet_server_addr()::text, inet_server_port()")) {
        rs.next();
        System.out.println("db="+rs.getString(1)+" host="+rs.getString(2)+" port="+rs.getString(3));
      }
      try (ResultSet rs = s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='tenant_demo' AND table_name='sys_config' AND column_name LIKE 'category%' OR (table_schema='tenant_demo' AND table_name='sys_config' AND column_name LIKE 'value%' OR (table_schema='tenant_demo' AND table_name='sys_config' AND column_name IN ('item_code','item_name','sort_order'))) ORDER BY 1")) {
        while (rs.next()) System.out.println("col="+rs.getString(1));
      }
      try (ResultSet rs = s.executeQuery("SELECT config_key FROM tenant_demo.sys_config ORDER BY 1")) {
        while (rs.next()) System.out.println("row="+rs.getString(1));
      }
    } catch (Exception e) {
      System.out.println("FAIL "+url+" : "+e.getMessage());
    }
  }
}