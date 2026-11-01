-- PostgreSQL 18 provides uuidv7() in pg_catalog. Supabase currently offers
-- PostgreSQL 17 projects as well, so install the same RFC 9562 UUIDv7 layout
-- there before any schema migration uses DEFAULT uuidv7().
DO $migration$
BEGIN
    IF pg_catalog.to_regprocedure('pg_catalog.uuidv7()') IS NULL THEN
        EXECUTE $function$
            CREATE FUNCTION public.uuidv7()
            RETURNS uuid
            LANGUAGE plpgsql
            VOLATILE
            SET search_path = pg_catalog
            AS $body$
            DECLARE
                timestamp_ms bigint := floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint;
                bytes bytea := uuid_send(gen_random_uuid());
            BEGIN
                -- RFC 9562: 48-bit Unix timestamp (milliseconds), version 7,
                -- 74 random bits, and the RFC variant bits (10xx).
                bytes := set_byte(bytes, 0, ((timestamp_ms >> 40) & 255)::integer);
                bytes := set_byte(bytes, 1, ((timestamp_ms >> 32) & 255)::integer);
                bytes := set_byte(bytes, 2, ((timestamp_ms >> 24) & 255)::integer);
                bytes := set_byte(bytes, 3, ((timestamp_ms >> 16) & 255)::integer);
                bytes := set_byte(bytes, 4, ((timestamp_ms >> 8) & 255)::integer);
                bytes := set_byte(bytes, 5, (timestamp_ms & 255)::integer);
                bytes := set_byte(bytes, 6, (get_byte(bytes, 6) & 15) | 112);
                bytes := set_byte(bytes, 8, (get_byte(bytes, 8) & 63) | 128);
                RETURN encode(bytes, 'hex')::uuid;
            END;
            $body$;
        $function$;
    END IF;
END;
$migration$;
