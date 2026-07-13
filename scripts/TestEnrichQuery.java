import java.sql.*;
public class TestEnrichQuery {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456")) {
      c.createStatement().execute("SET search_path TO tenant_demo, public");
      PreparedStatement ps = c.prepareStatement("SELECT * FROM repair_workorder_process WHERE workorder_id = ?::uuid AND action_type IN (?) ORDER BY created_at DESC LIMIT 1");
      ps.setObject(1, java.util.UUID.fromString("60d2ea3f-3310-462f-aebd-572047d5ee38"));
      ps.setString(2, "complete");
      ResultSet rs = ps.executeQuery();
      System.out.println("rows=" + (rs.next() ? 1 : 0));
    }
  }
}
