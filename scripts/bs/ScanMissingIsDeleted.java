import java.sql.*;
import java.util.*;
public class ScanMissingIsDeleted {
  static final Set<String> SKIP = Set.of(
    "flyway_schema_history", "flyway_schema_history_public"
  );
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis","med","med123456");
         Statement st = c.createStatement()) {
      for (String s : new String[]{"tenant_demo","tenant_hospdemo03","tenant_test"}) {
        st.execute("SET search_path TO " + s);
        System.out.println("=== " + s + " ===");
        try (ResultSet rs = st.executeQuery(
            "SELECT t.table_name FROM information_schema.tables t "
          + "WHERE t.table_schema = current_schema() AND t.table_type = 'BASE TABLE' "
          + "AND NOT EXISTS ("
          + "  SELECT 1 FROM information_schema.columns c "
          + "  WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name AND c.column_name = 'is_deleted'"
          + ") ORDER BY 1")) {
          List<String> miss = new ArrayList<>();
          while (rs.next()) {
            String name = rs.getString(1);
            if (SKIP.contains(name) || name.startsWith("flyway_")) continue;
            miss.add(name);
          }
          System.out.println("missing_count=" + miss.size());
          for (String n : miss) System.out.println(n);
        }
      }
    }
  }
}