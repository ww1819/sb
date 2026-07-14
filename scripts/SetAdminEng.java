import java.sql.*;
public class SetAdminEng {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis","med","med123456");
         Statement st = c.createStatement()) {
      for (String s : new String[] {"tenant_demo","tenant_hospdemo03","tenant_test"}) {
        st.execute("SET search_path TO "+s);
        int n = st.executeUpdate("UPDATE sys_user SET is_repair_engineer=true, updated_at=NOW() WHERE username='admin'");
        try (ResultSet rs = st.executeQuery("SELECT username, is_repair_engineer FROM sys_user WHERE username='admin'")) {
          rs.next();
          System.out.println(s+": updated="+n+" admin.eng="+rs.getBoolean(2));
        }
        try (ResultSet rs = st.executeQuery("SELECT to_regclass('engineer')")) {
          rs.next();
          System.out.println(s+": engineer_table="+rs.getString(1));
        }
      }
    }
  }
}