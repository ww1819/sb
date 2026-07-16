import java.sql.*;
public class VerifyIsDel {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis","med","med123456");
         Statement st = c.createStatement()) {
      for (String s : new String[]{"tenant_demo","tenant_hospdemo03","tenant_test"}) {
        st.execute("SET search_path TO "+s);
        for (String t : new String[]{"device_label_print_log","sys_entity_change_log"}) {
          try (ResultSet rs = st.executeQuery(
              "SELECT column_default, is_nullable, data_type FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='"+t+"' AND column_name='is_deleted'")) {
            if (!rs.next()) { System.out.println(s+"."+t+" MISSING"); continue; }
            System.out.println(s+"."+t+" type="+rs.getString(3)+" default="+rs.getString(1)+" null="+rs.getString(2));
          }
          try (ResultSet rs = st.executeQuery("SELECT count(*) FILTER (WHERE is_deleted=0), count(*) FILTER (WHERE is_deleted IS DISTINCT FROM 0), count(*) FROM "+t)) {
            rs.next();
            System.out.println("  rows0="+rs.getLong(1)+" rowsNon0="+rs.getLong(2)+" total="+rs.getLong(3));
          }
        }
      }
    }
  }
}