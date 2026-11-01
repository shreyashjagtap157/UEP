-- Flyway SQL callback, executed before versioned migrations.
-- PostgreSQL 18 resolves pg_catalog.uuidv7() first; this public fallback
-- supplies RFC 9562 UUIDv7 on PostgreSQL 17.
-- The compatibility function uses 48-bit Unix-millisecond time, 12 rand_a bits,
-- and 62 rand_b bits. From the source UUIDv4, positions 1-3 supply rand_a;
-- position 17 contributes its two random low bits plus variant 10; positions
-- 18-32 supply the remaining 60 rand_b bits.
CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $uuidv7$
  WITH source AS (
    SELECT
      lpad(to_hex(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0') AS timestamp_hex,
      replace(gen_random_uuid()::text, '-', '') AS random_hex
  ),
  bits AS (
    SELECT
      timestamp_hex,
      substring(random_hex, 1, 3) AS rand_a_hex,
      CASE substring(random_hex, 17, 1)
        WHEN '0' THEN '8' WHEN '4' THEN '8' WHEN '8' THEN '8' WHEN 'c' THEN '8'
        WHEN '1' THEN '9' WHEN '5' THEN '9' WHEN '9' THEN '9' WHEN 'd' THEN '9'
        WHEN '2' THEN 'a' WHEN '6' THEN 'a' WHEN 'a' THEN 'a' WHEN 'e' THEN 'a'
        WHEN '3' THEN 'b' WHEN '7' THEN 'b' WHEN 'b' THEN 'b' WHEN 'f' THEN 'b'
      END AS variant_nibble,
      substring(random_hex, 18, 15) AS rand_b_hex
    FROM source
  )
  SELECT (
    substring(timestamp_hex, 1, 8) || '-' ||
    substring(timestamp_hex, 9, 4) || '-' ||
    '7' || rand_a_hex || '-' ||
    variant_nibble || substring(rand_b_hex, 1, 3) || '-' ||
    substring(rand_b_hex, 4, 12)
  )::uuid
  FROM bits;
$uuidv7$;
