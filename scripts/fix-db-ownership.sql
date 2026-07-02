-- Fix tables/sequences created by postgres superuser so Flyway (med) can migrate
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT schemaname, tablename FROM pg_tables
           WHERE tableowner = 'postgres'
             AND schemaname NOT IN ('pg_catalog', 'information_schema')
  LOOP
    EXECUTE format('ALTER TABLE %I.%I OWNER TO med', r.schemaname, r.tablename);
  END LOOP;
  FOR r IN SELECT sequence_schema AS schemaname, sequence_name AS seqname
           FROM information_schema.sequences
           WHERE sequence_schema NOT IN ('pg_catalog', 'information_schema')
  LOOP
    EXECUTE format('ALTER SEQUENCE %I.%I OWNER TO med', r.schemaname, r.seqname);
  END LOOP;
END $$;

DO $$
DECLARE s TEXT;
BEGIN
  FOR s IN SELECT nspname FROM pg_namespace WHERE nspname LIKE 'tenant_%'
  LOOP
    EXECUTE format('ALTER SCHEMA %I OWNER TO med', s);
    EXECUTE format('GRANT ALL ON SCHEMA %I TO med', s);
    EXECUTE format('GRANT ALL ON ALL TABLES IN SCHEMA %I TO med', s);
    EXECUTE format('GRANT ALL ON ALL SEQUENCES IN SCHEMA %I TO med', s);
  END LOOP;
END $$;

GRANT ALL ON ALL TABLES IN SCHEMA public TO med;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO med;
