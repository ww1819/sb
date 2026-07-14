SELECT nspname AS schema
FROM pg_namespace
WHERE nspname NOT LIKE 'pg_%'
  AND nspname <> 'information_schema'
ORDER BY 1;

SELECT table_schema, column_name, data_type, column_default, is_nullable
FROM information_schema.columns
WHERE table_name = 'sys_user'
  AND column_name IN ('is_repair_engineer', 'is_deleted')
ORDER BY table_schema, column_name;
