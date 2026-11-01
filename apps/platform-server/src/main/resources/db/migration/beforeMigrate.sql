-- Runs before every Flyway migrate, including the first versioned migration.
-- PostgreSQL 18 provides uuidv7() in pg_catalog. PostgreSQL 17 does not.
-- pg_catalog is searched before public by default, so the native PostgreSQL 18
-- function remains authoritative while this public compatibility function
-- supplies UUIDv7 on PostgreSQL 17.
CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $uuidv7$
  WITH parts AS (
    SELECT
      lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0') AS timestamp_hex,
      replace(gen_random_uuid()::text, '-', '') AS random_hex
  )
  SELECT (
    substring(timestamp_hex, 1, 8) || '-' ||
    substring(timestamp_hex, 9, 4) || '-' ||
    '7' || substring(random_hex, 14, 3) || '-' ||
    substring(random_hex, 17, 4) || '-' ||
    substring(random_hex, 21, 12)
  )::uuid
  FROM parts;
$uuidv7$;
