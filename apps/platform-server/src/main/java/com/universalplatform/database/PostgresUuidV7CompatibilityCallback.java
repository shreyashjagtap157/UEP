package com.universalplatform.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/**
 * Supplies RFC 9562 UUIDv7 generation on PostgreSQL versions that do not provide
 * the native pg_catalog.uuidv7() function (notably PostgreSQL 17).
 *
 * <p>This is deliberately a Flyway lifecycle callback rather than a connection
 * init-SQL: Flyway creates and checks its schema-history table before
 * BEFORE_EACH_MIGRATE fires. The function is therefore installed immediately
 * before the first migration, without making a pristine schema look non-empty
 * to Flyway's baseline guard and without changing any migration checksum.</p>
 */
@Component
final class PostgresUuidV7CompatibilityCallback implements Callback {
    private static final String NATIVE_FUNCTION_QUERY =
            "SELECT pg_catalog.to_regprocedure('pg_catalog.uuidv7()') IS NOT NULL";

    private static final String CREATE_UUID_V7_FUNCTION = """
            CREATE OR REPLACE FUNCTION public.uuidv7()
            RETURNS uuid
            LANGUAGE sql
            VOLATILE
            SET search_path = pg_catalog
            AS $function$
            WITH entropy AS (
              SELECT
                pg_catalog.decode(
                  pg_catalog.lpad(
                    pg_catalog.to_hex(
                      floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint
                    ),
                    12,
                    '0'
                  ),
                  'hex'
                ) AS time_bytes,
                substring(pg_catalog.uuid_send(pg_catalog.gen_random_uuid()) FROM 7 FOR 10) AS random_bytes
            ),
            versioned AS (
              SELECT
                time_bytes,
                pg_catalog.set_byte(
                  random_bytes,
                  0,
                  (pg_catalog.get_byte(random_bytes, 0) & 15) | 112
                ) AS random_bytes
              FROM entropy
            ),
            variant AS (
              SELECT
                time_bytes,
                pg_catalog.set_byte(
                  random_bytes,
                  2,
                  (pg_catalog.get_byte(random_bytes, 2) & 63) | 128
                ) AS random_bytes
              FROM versioned
            )
            SELECT pg_catalog.encode(time_bytes || random_bytes, 'hex')::uuid
            FROM variant
            $function$
            """;

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        if (event != Event.BEFORE_EACH_MIGRATE) {
            return;
        }

        Connection connection = context.getConnection();
        try {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement(NATIVE_FUNCTION_QUERY);
                    ResultSet result = statement.executeQuery()) {
                if (result.next() && result.getBoolean(1)) {
                    return;
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_UUID_V7_FUNCTION);
            }
        } catch (SQLException exception) {
            throw new FlywayException(
                    "Unable to install the PostgreSQL 17 UUIDv7 compatibility function before migration execution.",
                    exception);
        }
    }

    @Override
    public String getCallbackName() {
        return "PostgreSQL UUIDv7 compatibility";
    }
}
