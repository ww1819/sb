-- REP-03：维修工程师改 sys_user.is_repair_engineer；维修工单负责人改 assigned_user_id
-- 镜像（历史）：meis-tenant/.../R__data_fix.sql 工程师迁移段落；【DEPRECATED】勿再扩展

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS is_repair_engineer BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE sys_user u
SET is_repair_engineer = TRUE
FROM engineer e
WHERE e.user_id = u.id
  AND COALESCE(u.is_repair_engineer, FALSE) = FALSE;

ALTER TABLE repair_workorder ADD COLUMN IF NOT EXISTS assigned_user_id UUID;

UPDATE repair_workorder w
SET assigned_user_id = e.user_id
FROM engineer e
WHERE w.assigned_engineer_id = e.id
  AND w.assigned_user_id IS NULL;

UPDATE repair_workorder w
SET assigned_user_id = w.assigned_engineer_id
WHERE w.assigned_user_id IS NULL
  AND w.assigned_engineer_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.assigned_engineer_id);

DO $rep03_drop_wo_eng$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'repair_workorder' AND column_name = 'assigned_engineer_id'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'repair_workorder' AND column_name = 'assigned_user_id'
    ) THEN
        ALTER TABLE repair_workorder DROP CONSTRAINT IF EXISTS repair_workorder_assigned_engineer_id_fkey;
        ALTER TABLE repair_workorder DROP COLUMN assigned_engineer_id;
    END IF;
END $rep03_drop_wo_eng$;

DO $rep03_rename_process$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'user_id')
    THEN
        ALTER TABLE repair_workorder_process RENAME COLUMN engineer_id TO user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'from_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'from_user_id')
    THEN
        ALTER TABLE repair_workorder_process RENAME COLUMN from_engineer_id TO from_user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'to_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_process' AND column_name = 'to_user_id')
    THEN
        ALTER TABLE repair_workorder_process RENAME COLUMN to_engineer_id TO to_user_id;
    END IF;
END $rep03_rename_process$;

UPDATE repair_workorder_process p
SET user_id = e.user_id
FROM engineer e
WHERE p.user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = p.user_id);

UPDATE repair_workorder_process p
SET from_user_id = e.user_id
FROM engineer e
WHERE p.from_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = p.from_user_id);

UPDATE repair_workorder_process p
SET to_user_id = e.user_id
FROM engineer e
WHERE p.to_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = p.to_user_id);

DO $rep03_rename_event$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'user_id')
    THEN
        ALTER TABLE repair_workorder_event RENAME COLUMN engineer_id TO user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'from_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'from_user_id')
    THEN
        ALTER TABLE repair_workorder_event RENAME COLUMN from_engineer_id TO from_user_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'to_engineer_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'repair_workorder_event' AND column_name = 'to_user_id')
    THEN
        ALTER TABLE repair_workorder_event RENAME COLUMN to_engineer_id TO to_user_id;
    END IF;
END $rep03_rename_event$;

UPDATE repair_workorder_event ev
SET user_id = e.user_id
FROM engineer e
WHERE ev.user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = ev.user_id);

UPDATE repair_workorder_event ev
SET from_user_id = e.user_id
FROM engineer e
WHERE ev.from_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = ev.from_user_id);

UPDATE repair_workorder_event ev
SET to_user_id = e.user_id
FROM engineer e
WHERE ev.to_user_id = e.id
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = ev.to_user_id);

CREATE INDEX IF NOT EXISTS idx_wo_assigned_user ON repair_workorder(assigned_user_id);
