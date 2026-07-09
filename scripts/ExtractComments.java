import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ExtractComments {
    static final Path MIGRATIONS = Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant");
    static final Path OUT = Paths.get("d:/sb_project/sb/db/source/patches/tenant_comment_backfill.sql");
    static final Path FLYWAY = Paths.get("d:/sb_project/sb/meis-tenant/src/main/resources/db/migrations/tenant/V18__comment_backfill.sql");

    static final Path PATCHES = Paths.get("d:/sb_project/sb/db/source/patches");

    public static void main(String[] args) throws Exception {
        List<Path> inputs = new ArrayList<>();
        for (String f : List.of("V1__tables.sql", "V2__extensions.sql", "V4__comments.sql", "V17__tenant_schema_sync.sql")) {
            Path path = MIGRATIONS.resolve(f);
            if (Files.exists(path)) inputs.add(path);
        }
        Path viewComments = PATCHES.resolve("tenant_view_comments.sql");
        if (Files.exists(viewComments)) inputs.add(viewComments);
        LinkedHashMap<String, String> comments = new LinkedHashMap<>();
        Pattern p = Pattern.compile("^COMMENT ON (TABLE|COLUMN|INDEX)\\s+([\\w.]+)\\s+IS\\s+'((?:''|[^'])*)'\\s*;\\s*$",
                Pattern.CASE_INSENSITIVE);

        for (Path path : inputs) {
            if (!Files.exists(path)) continue;
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (!trimmed.toUpperCase(Locale.ROOT).startsWith("COMMENT ON")) continue;
                Matcher m = p.matcher(trimmed);
                if (!m.find()) continue;
                String key = m.group(1).toUpperCase(Locale.ROOT) + ":" + m.group(2).toLowerCase();
                comments.put(key, trimmed);
            }
        }

        List<String> out = new ArrayList<>();
        out.add("-- =============================================================================");
        out.add("-- MEIS tenant comment backfill (idempotent: COMMENT ON overwrites)");
        out.add("-- Source: V1 + V2 + V4 + V17 migration scripts");
        out.add("-- Usage: scripts/apply-tenant-comments.ps1 -Schema tenant_demo");
        out.add("-- =============================================================================");
        out.add("");
        comments.values().forEach(out::add);
        out.add("");

        String content = String.join("\n", out) + "\n";
        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, content);
        Files.writeString(FLYWAY, "-- Flyway: full comment backfill for legacy tenant schemas\n\n" + content);
        System.out.println("Extracted " + comments.size() + " COMMENT ON statements");
        System.out.println("Wrote " + OUT);
        System.out.println("Wrote " + FLYWAY);
    }
}
