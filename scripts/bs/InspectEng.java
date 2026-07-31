import java.sql.*;
public class InspectEng {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:postgresql://localhost:5432/meis";
    try (Connection c = DriverManager.getConnection(url, "med", "med123456");
         Statement st = c.createStatement()) {
      for (String s : new String[] {"tenant_demo", "tenant_hospdemo03", "tenant_test"}) {
        st.execute("SET search_path TO " + s);
        System.out.println("=== " + s + " ===");
        try (ResultSet rs = st.executeQuery(
            "SELECT username, real_name, is_repair_engineer FROM sys_user WHERE COALESCE(is_deleted,0)=0 ORDER BY username LIMIT 30")) {
          while (rs.next()) {
            System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | eng=" + rs.getBoolean(3));
          }
        }
        try (ResultSet rs = st.executeQuery(
            "SELECT count(*) FROM sys_user WHERE is_repair_engineer=true AND COALESCE(is_deleted,0)=0")) {
          rs.next();
          System.out.println("engineer_count=" + rs.getInt(1));
        }
        try (ResultSet rs = st.executeQuery(
            "SELECT count(*) FROM information_schema.columns c"
                + " JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name AND t.table_type='BASE TABLE'"
                + " WHERE c.table_schema=current_schema() AND c.column_name='is_deleted'"
                + " AND (c.column_default IS NULL OR position('0' in coalesce(c.column_default,''))=0)")) {
          rs.next();
          System.out.println("is_deleted_cols_without_0_default=" + rs.getInt(1));
        }
        try (ResultSet rs = st.executeQuery(
            "SELECT count(*) FROM information_schema.columns c"
                + " JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name AND t.table_type='BASE TABLE'"
                + " WHERE c.table_schema=current_schema() AND c.column_name='is_deleted'")) {
          rs.next();
          System.out.println("is_deleted_col_tables=" + rs.getInt(1));
        }
      }
    }
  }
}