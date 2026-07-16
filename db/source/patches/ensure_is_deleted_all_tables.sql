-- 租户 schema：为缺 is_deleted 的基表补列（DEFAULT 0），并回填存量
-- 排除 flyway_%；同步补 deleted_at / deleted_by

ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE device_label_print_log ADD COLUMN IF NOT EXISTS deleted_by UUID;
ALTER TABLE sys_entity_change_log ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sys_entity_change_log ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE sys_entity_change_log ADD COLUMN IF NOT EXISTS deleted_by UUID;

DO $ensure_is_deleted_all$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT t.table_name
        FROM information_schema.tables t
        WHERE t.table_schema = current_schema()
          AND t.table_type = 'BASE TABLE'
          AND t.table_name NOT LIKE 'flyway_%'
          AND NOT EXISTS (
              SELECT 1 FROM information_schema.columns c
              WHERE c.table_schema = t.table_schema
                AND c.table_name = t.table_name
                AND c.column_name = 'is_deleted'
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN is_deleted SMALLINT NOT NULL DEFAULT 0',
            r.table_name
        );
        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ',
            r.table_name
        );
        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN IF NOT EXISTS deleted_by UUID',
            r.table_name
        );
        EXECUTE format(
            'UPDATE %I SET is_deleted = 1 WHERE deleted_at IS NOT NULL AND is_deleted = 0',
            r.table_name
        );
    END LOOP;
END $ensure_is_deleted_all$;

DO $fix_is_deleted_defaults$
DECLARE
    r RECORD;
    has_deleted_at BOOLEAN;
BEGIN
    FOR r IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
         AND t.table_type = 'BASE TABLE'
        WHERE c.table_schema = current_schema()
          AND c.column_name = 'is_deleted'
          AND c.table_name NOT LIKE 'flyway_%'
    LOOP
        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns x
            WHERE x.table_schema = current_schema()
              AND x.table_name = r.table_name
              AND x.column_name = 'deleted_at'
        ) INTO has_deleted_at;

        IF has_deleted_at THEN
            EXECUTE format(
                'UPDATE %I SET is_deleted = 0 WHERE deleted_at IS NULL AND COALESCE(is_deleted, 0) <> 0',
                r.table_name
            );
            EXECUTE format(
                'UPDATE %I SET is_deleted = 1 WHERE deleted_at IS NOT NULL AND COALESCE(is_deleted, 0) = 0',
                r.table_name
            );
        END IF;
        EXECUTE format(
            'UPDATE %I SET is_deleted = 0 WHERE is_deleted IS NULL',
            r.table_name
        );
        EXECUTE format('ALTER TABLE %I ALTER COLUMN is_deleted SET DEFAULT 0', r.table_name);
        BEGIN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN is_deleted SET NOT NULL', r.table_name);
        EXCEPTION WHEN others THEN
            NULL;
        END;
    END LOOP;
END $fix_is_deleted_defaults$;
