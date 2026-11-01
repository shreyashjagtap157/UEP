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
        WHEN '0' THEN '8' WHEN '1' THEN '9' WHEN '2' THEN 'a' WHEN '3' THEN 'b'
        WHEN '4' THEN '8' WHEN '5' THEN '9' WHEN '6' THEN 'a' WHEN '7' THEN 'b'
        WHEN '8' THEN '8' WHEN '9' THEN '9' WHEN 'a' THEN 'a' WHEN 'b' THEN 'b'
        WHEN 'c' THEN '8' WHEN 'd' THEN '9' WHEN 'e' THEN 'a' WHEN 'f' THEN 'b'
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
