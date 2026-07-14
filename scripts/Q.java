import java.sql.*;
public class Q {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456");
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='tenant_demo' AND table_name='sys_config' ORDER BY ordinal_position")) {
      while (rs.next()) System.out.println(rs.getString(1));
    }
  }
}