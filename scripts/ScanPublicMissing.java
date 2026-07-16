import java.sql.*;
public class ScanPublicMissing {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis","med","med123456");
         Statement st = c.createStatement()) {
      st.execute("SET search_path TO public");
      System.out.println("=== public tables missing is_deleted ===");
      try (ResultSet rs = st.executeQuery(
          "SELECT t.table_name FROM information_schema.tables t "
        + "WHERE t.table_schema='public' AND t.table_type='BASE TABLE' "
        + "AND NOT EXISTS (SELECT 1 FROM information_schema.columns c "
        + " WHERE c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name='is_deleted') "
        + "AND t.table_name NOT LIKE 'flyway_%' ORDER BY 1")) {
        while (rs.next()) System.out.println(rs.getString(1));
      }
      for (String tbl : new String[]{"device_label_print_log","sys_entity_change_log"}) {
        st.execute("SET search_path TO tenant_demo");
        System.out.println("--- " + tbl + " columns ---");
        try (ResultSet rs = st.executeQuery(
            "SELECT column_name, data_type, column_default, is_nullable "
          + "FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='" + tbl + "' ORDER BY ordinal_position")) {
          while (rs.next()) System.out.println(rs.getString(1)+" | "+rs.getString(2)+" | def="+rs.getString(3)+" | null="+rs.getString(4));
        }
        try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + tbl)) {
          rs.next(); System.out.println("row_count="+rs.getLong(1));
        }
      }
    }
  }
}