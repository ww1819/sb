import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class ApplySql {
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: ApplySql host port db user pass schema sqlFile");
            System.exit(1);
        }
        String url = "jdbc:postgresql://" + args[0] + ":" + args[1] + "/" + args[2];
        String sql = stripComments(new String(Files.readAllBytes(Paths.get(args[6])), "UTF-8"));
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

    /** Split on ';' outside single quotes and dollar-quoted bodies ($tag$...$tag$). */
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        String dollarTag = null;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (dollarTag != null) {
                if (sql.startsWith(dollarTag, i)) {
                    cur.append(dollarTag);
                    i += dollarTag.length() - 1;
                    dollarTag = null;
                } else {
                    cur.append(ch);
                }
                continue;
            }
            if (!inSingle && ch == '$') {
                int end = findDollarTagEnd(sql, i);
                if (end > i) {
                    dollarTag = sql.substring(i, end + 1);
                    cur.append(dollarTag);
                    i = end;
                    continue;
                }
            }
            if (ch == '\'') {
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    cur.append("''");
                    i++;
                    continue;
                }
                inSingle = !inSingle;
            }
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

    static int findDollarTagEnd(String sql, int i) {
        int j = i + 1;
        while (j < sql.length()) {
            char c = sql.charAt(j);
            if (c == '$') return j;
            if (!(Character.isLetterOrDigit(c) || c == '_')) return -1;
            j++;
        }
        return -1;
    }
}
