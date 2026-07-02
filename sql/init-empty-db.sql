-- 创建应用用户与空库（由 setup-postgres.ps1 调用）
-- 默认库名: sb  用户: med

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'med') THEN
    CREATE ROLE med LOGIN PASSWORD 'med123456';
  ELSE
    ALTER ROLE med WITH PASSWORD 'med123456';
  END IF;
END
$$;

-- 注意: CREATE DATABASE 不能在 DO 块内执行，由 psql -c 单独执行
