-- Runs before every Flyway migrate, including the first versioned migration.
-- PostgreSQL 18 resolves pg_catalog.uuidv7() before this public compatibility function.
-- PostgreSQL 17 uses this RFC 9562 UUIDv7 implementation.
-- The timestamp is Unix milliseconds; random bits come from pg_catalog.gen_random_uuid().
CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $uuidv7$
  WITH parts AS (
    SELECT
      lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0') AS timestamp_hex,
      replace(gen_random_uuid()::text, '-', '') AS random_hex
  ),
  bits AS (
    SELECT
      timestamp_hex,
      random_hex,
      CASE substring(random_hex, 4, 1)
        WHEN '0' THEN '8' WHEN '1' THEN '8' WHEN '2' THEN '8' WHEN '3' THEN '8'
        WHEN '4' THEN '9' WHEN '5' THEN '9' WHEN '6' THEN '9' WHEN '7' THEN '9'
        WHEN '8' THEN 'a' WHEN '9' THEN 'a' WHEN 'a' THEN 'a' WHEN 'b' THEN 'a'
        WHEN 'c' THEN 'b' WHEN 'd' THEN 'b' WHEN 'e' THEN 'b' WHEN 'f' THEN 'b'
      END AS variant_nibble
    FROM parts
  )
  SELECT (
    substring(timestamp_hex, 1, 8) || '-' ||
    substring(timestamp_hex, 9, 4) || '-' ||
    '7' || substring(random_hex, 1, 3) || '-' ||
    variant_nibble || substring(random_hex, 5, 3) || '-' ||
    substring(random_hex, 8, 12)
  )::uuid
  FROM bits;
$uuidv7$;
