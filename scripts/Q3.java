import java.sql.*;
public class Q3 {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456");
         Statement s = c.createStatement()) {
      s.execute("SET search_path TO tenant_demo");
      try {
        s.executeUpdate("INSERT INTO sys_config (id, config_key, config_value, config_type, description, is_system, category_code, category_name, item_code, item_name, value1, value2, value3, value4, value5, value6, sort_order, is_deleted) VALUES ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid, 'k_test_x1', 'v', 'string', 'd', false, '01', '测试', '1', '测试1', null, null, null, null, null, null, 0, 0)");
        System.out.println("INSERT OK");
      } catch (SQLException e) {
        System.out.println("INSERT FAIL: " + e.getMessage());
      }
    }
  }
}