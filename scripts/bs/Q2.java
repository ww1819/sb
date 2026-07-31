import java.sql.*;
public class Q2 {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://43.138.177.53:5432/meis","med","med123456");
         Statement s = c.createStatement()) {
      ResultSet rs = s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='sys_config' ORDER BY ordinal_position");
      System.out.println("=== public.sys_config ===");
      boolean any=false;
      while (rs.next()) { any=true; System.out.println(rs.getString(1)); }
      if (!any) System.out.println("(none)");
      rs = s.executeQuery("SELECT nspname FROM pg_namespace WHERE nspname LIKE 'tenant%' ORDER BY 1");
      System.out.println("=== schemas ===");
      while (rs.next()) System.out.println(rs.getString(1));
    }
  }
}