-- =============================================================================
-- 1) REP-03：补齐维修工程师标志（根因：派工下拉/添加进程依赖此列）
-- 2) 软删标志：未删除数据 is_deleted=0，并保证 DEFAULT 0 / NOT NULL
-- =============================================================================

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS is_repair_engineer BOOLEAN NOT NULL DEFAULT FALSE;

DO $backfill_eng$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'engineer'
    ) THEN
        UPDATE sys_user u
        SET is_repair_engineer = TRUE
        FROM engineer e
        WHERE e.user_id = u.id
          AND COALESCE(u.is_repair_engineer, FALSE) = FALSE;
    END IF;
END $backfill_eng$;

DO $fix_is_deleted$
DECLARE
    r RECORD;
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
    LOOP
        -- 未删行（无 deleted_at）统一标记为 0
        EXECUTE format(
            'UPDATE %I SET is_deleted = 0 WHERE deleted_at IS NULL AND COALESCE(is_deleted, 0) <> 0',
            r.table_name
        );
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
END $fix_is_deleted$;
