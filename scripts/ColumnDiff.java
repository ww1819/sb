import java.sql.*;
import java.util.*;

public class ColumnDiff {
    static final String[][] CHECKS = {
        {"inventory_check", "audit_status"},
        {"medical_device", "pinyin_code"},
        {"medical_device", "extension_data"},
        {"medical_device", "manual_files"},
        {"department", "pinyin_code"},
        {"supplier", "pinyin_code"},
        {"manufacturer", "pinyin_code"},
        {"import_template_field", "field_key"},
        {"sys_notification", "message_type"},
        {"sys_notification", "notification_type"},
        {"sys_notification", "user_id"},
        {"device_scrap", "approval_status"},
        {"asset_transfer", "approval_status"},
        {"purchase_plan", "business_chain_no"},
        {"warehouse", "warehouse_code"},
        {"import_profile_binding", "profile_code"},
    };

    public static void main(String[] args) throws Exception {
        String schema = args.length > 0 ? args[0] : "tenant_demo";
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/meis", "med", "med123456")) {
            System.out.println("schema=" + schema);
            for (String[] c : CHECKS) {
                boolean exists = columnExists(conn, schema, c[0], c[1]);
                System.out.println((exists ? "YES" : "NO ") + " " + c[0] + "." + c[1]);
            }
            System.out.println("\nAll tables:");
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) System.out.println(rs.getString(1));
                }
            }
        }
    }

    static boolean columnExists(Connection conn, String schema, String table, String col) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ? AND column_name = ?
            """)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, col);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
}
