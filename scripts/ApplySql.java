import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class ApplySql {
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: ApplySql host port db user pass schema sqlFile");
            System.exit(1);
        }
        String url = "jdbc:postgresql://" + args[0] + ":" + args[1] + "/" + args[2];
        String sql = stripComments(Files.readString(Path.of(args[6])));
        try (Connection conn = DriverManager.getConnection(url, args[3], args[4])) {
            conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                st.execute("SET search_path TO " + args[5]);
                for (String stmt : splitStatements(sql)) {
                    st.execute(stmt);
                }
            }
        }
        System.out.println("SQL applied to schema " + args[5]);
    }

    static String stripComments(String sql) {
        return sql.replaceAll("(?m)^\\s*--.*$", "").trim();
    }

    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) inSingle = !inSingle;
            if (ch == ';' && !inSingle) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur = new StringBuilder();
                continue;
            }
            cur.append(ch);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }
}
