import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Export public sys_menu + package menus into R__menus.sql */
public class ExportMenus {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:postgresql://43.138.177.53:5432/meis";
    try (Connection c = DriverManager.getConnection(url, "med", "med123456")) {
      c.setSchema("public");
      StringBuilder sb = new StringBuilder();
      sb.append("-- MEIS public 菜单唯一维护脚本（PLT-MENU-01）\n");
      sb.append("-- 幂等：可重复执行。菜单变更只改本文件，勿写入 R__data_fix 或其他脚本。\n");
      sb.append("-- Flyway 槽位：public/R__menus.sql（与 R__data_fix 并列；本文件专管菜单）。\n\n");

      sb.append("-- ========== 1. sys_menu ==========\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery(
               "SELECT menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, COALESCE(is_active,true) AS is_active "
                   + "FROM sys_menu ORDER BY CASE WHEN parent_code IS NULL THEN 0 ELSE 1 END, sort_order NULLS LAST, menu_code")) {
        while (rs.next()) {
          String code = rs.getString(1);
          String parent = rs.getString(2);
          String name = rs.getString(3);
          String type = rs.getString(4);
          String path = rs.getString(5);
          String icon = rs.getString(6);
          int sort = rs.getInt(7);
          if (rs.wasNull()) sort = 0;
          boolean active = rs.getBoolean(8);
          sb.append("INSERT INTO sys_menu (menu_code, parent_code, menu_name, menu_type, path, icon, sort_order, is_active) VALUES (");
          sb.append(sqlStr(code)).append(", ").append(sqlStr(parent)).append(", ").append(sqlStr(name)).append(", ");
          sb.append(sqlStr(type)).append(", ").append(sqlStr(path)).append(", ").append(sqlStr(icon)).append(", ");
          sb.append(sort).append(", ").append(active ? "TRUE" : "FALSE").append(")\n");
          sb.append("ON CONFLICT (menu_code) DO UPDATE SET parent_code=EXCLUDED.parent_code, menu_name=EXCLUDED.menu_name, menu_type=EXCLUDED.menu_type, path=EXCLUDED.path, icon=EXCLUDED.icon, sort_order=EXCLUDED.sort_order, is_active=EXCLUDED.is_active;\n");
        }
      }

      sb.append("\n-- ========== 2. sys_package_menu ==========\n");
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery("SELECT package_code, menu_code FROM sys_package_menu ORDER BY 1,2")) {
        List<String> rows = new ArrayList<>();
        while (rs.next()) {
          rows.add("('" + esc(rs.getString(1)) + "','" + esc(rs.getString(2)) + "')");
        }
        for (int i = 0; i < rows.size(); i += 80) {
          int end = Math.min(i + 80, rows.size());
          sb.append("INSERT INTO sys_package_menu (package_code, menu_code) VALUES\n");
          sb.append(String.join(",\n", rows.subList(i, end)));
          sb.append("\nON CONFLICT DO NOTHING;\n\n");
        }
      }

      sb.append("-- ========== 3. sys_tenant_menu：活跃租户挂接其套餐菜单 ==========\n");
      sb.append("INSERT INTO sys_tenant_menu (tenant_id, menu_code)\n");
      sb.append("SELECT t.id, pm.menu_code\n");
      sb.append("FROM sys_tenant t\n");
      sb.append("JOIN sys_package_menu pm ON pm.package_code = COALESCE(t.package_code, 'standard')\n");
      sb.append("WHERE t.status = 'active'\n");
      sb.append("ON CONFLICT DO NOTHING;\n");

      Path out = Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/public/R__menus.sql");
      Files.writeString(out, sb.toString());
      System.out.println("Wrote " + out + " chars=" + sb.length());
    }
  }

  static String esc(String s) {
    return s == null ? null : s.replace("'", "''");
  }

  static String sqlStr(String s) {
    return s == null ? "NULL" : ("'" + esc(s) + "'");
  }
}
