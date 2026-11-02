package com.universalplatform.storage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * Persistent storage-capacity ledger. Reservations and aggregates are changed in one transaction;
 * provider and tenant rows are locked in a fixed order so concurrent uploads cannot over-allocate.
 * This deliberately does not use the daily usage_counter table.
 */
@Service
public class StorageQuotaService {
    private final JdbcTemplate jdbc;

    public StorageQuotaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID reserve(UUID tenantId, StorageProviderType provider, String reservationKey,
                        String objectKey, long expectedBytes) {
        return reserveInternal(tenantId, provider, reservationKey, objectKey, expectedBytes);
    }

    /** Persists a reservation independently of an enclosing worker transaction before remote I/O. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reserveDurable(UUID tenantId, StorageProviderType provider, String reservationKey,
                               String objectKey, long expectedBytes) {
        return reserveInternal(tenantId, provider, reservationKey, objectKey, expectedBytes);
    }

    private UUID reserveInternal(UUID tenantId, StorageProviderType provider, String reservationKey,
                                 String objectKey, long expectedBytes) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(provider, "provider");
        if (reservationKey == null || reservationKey.isBlank() || reservationKey.length() > 200) {
            throw new IllegalArgumentException("A bounded storage reservation key is required");
        }
        if (expectedBytes < 0) throw new IllegalArgumentException("Reserved storage bytes cannot be negative");

        ProviderQuota account = lockProvider(provider);
        if (!account.writesEnabled()) throw StorageQuotaException.providerUnavailable(provider.name());

        jdbc.update("""
                INSERT INTO storage_tenant_usage(tenant_id)
                VALUES (?)
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId);
        TenantUsage tenant = lockTenant(tenantId);

        List<Reservation> existing = jdbc.query("""
                SELECT id, provider, state, reserved_bytes, committed_bytes, object_locator, object_key
                FROM storage_quota_reservation
                WHERE tenant_id = ? AND reservation_key = ?
                FOR UPDATE
                """, (rs, row) -> new Reservation(
                rs.getObject("id", UUID.class), rs.getString("provider"), rs.getString("state"),
                rs.getLong("reserved_bytes"), rs.getLong("committed_bytes"), rs.getString("object_locator"),
                rs.getString("object_key")),
                tenantId, reservationKey);
        if (!existing.isEmpty()) {
            Reservation reservation = existing.getFirst();
            if (!reservation.provider().equals(provider.name())) {
                throw StorageQuotaException.invalidReservation("provider changed for " + reservationKey);
            }
            if ("RESERVED".equals(reservation.state()) && reservation.reservedBytes() == expectedBytes
                    && Objects.equals(reservation.objectKey(), objectKey)) {
                return reservation.id();
            }
            if ("COMMITTED".equals(reservation.state()) && reservation.committedBytes() == expectedBytes
                    && (objectKey == null || Objects.equals(reservation.objectKey(), objectKey))) {
                return reservation.id();
            }
            throw StorageQuotaException.invalidReservation("key is already " + reservation.state());
        }

        if (account.maxObjectBytes() != null && expectedBytes > account.maxObjectBytes()) {
            throw StorageQuotaException.capacityExceeded("maximum object size");
        }

        long nextTenant = add(tenant.reservedBytes(), tenant.committedBytes(), expectedBytes);
        List<Long> entitlementLimits = jdbc.query("""
                SELECT hard_limit
                FROM entitlement_limit
                WHERE tenant_id = ? AND limit_key = 'STORAGE_BYTES'
                FOR UPDATE
                """, (rs, row) -> rs.getLong(1), tenantId);
        if (!entitlementLimits.isEmpty() && nextTenant > entitlementLimits.getFirst()) {
            throw StorageQuotaException.capacityExceeded("tenant");
        }

        long nextProvider = add(account.reservedBytes(), account.committedBytes(), expectedBytes);
        if (account.hardLimitBytes() != null && nextProvider > account.hardLimitBytes()) {
            throw StorageQuotaException.capacityExceeded("storage provider account");
        }

        UUID reservationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO storage_quota_reservation(
                    id, tenant_id, provider, reservation_key, state, reserved_bytes, committed_bytes,
                    object_key, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'RESERVED', ?, 0, ?, ?, ?)
                """, reservationId, tenantId, provider.name(), reservationKey, expectedBytes,
                objectKey, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                UPDATE storage_tenant_usage
                SET reserved_bytes = reserved_bytes + ?, row_version = row_version + 1
                WHERE tenant_id = ?
                """, expectedBytes, tenantId), "tenant reservation reserve");
        requireOne(jdbc.update("""
                UPDATE storage_provider_quota
                SET reserved_bytes = reserved_bytes + ?, updated_at = CURRENT_TIMESTAMP
                WHERE provider = ?
                """, expectedBytes, provider.name()), "provider reservation reserve");
        return reservationId;
    }

    @Transactional(readOnly = true)
    public void authorizeWrite(UUID tenantId, StorageProviderType provider) {
        List<Boolean> enabled = jdbc.query("""
                SELECT writes_enabled FROM storage_provider_quota WHERE provider = ?
                """, (rs, row) -> rs.getBoolean(1), provider.name());
        if (enabled.isEmpty() || !enabled.getFirst()) {
            throw StorageQuotaException.providerUnavailable(provider.name());
        }
    }

    @Transactional(readOnly = true)
    public void authorizeRead(UUID tenantId, StorageProviderType provider) {
        List<Boolean> enabled = jdbc.query("""
                SELECT reads_enabled FROM storage_provider_quota WHERE provider = ?
                """, (rs, row) -> rs.getBoolean(1), provider.name());
        if (enabled.isEmpty() || !enabled.getFirst()) {
            throw StorageQuotaException.providerUnavailable(provider.name());
        }
    }

    @Transactional(readOnly = true)
    public void authorizeDelete(UUID tenantId, StorageProviderType provider) {
        List<Boolean> configured = jdbc.query("""
                SELECT true FROM storage_provider_quota WHERE provider = ?
                """, (rs, row) -> rs.getBoolean(1), provider.name());
        if (configured.isEmpty()) {
            throw StorageQuotaException.providerUnavailable(provider.name());
        }
    }

    @Transactional
    public void commit(UUID tenantId, UUID reservationId, long actualBytes, String objectLocator, String objectKey) {
        if (actualBytes < 0 || objectLocator == null || objectLocator.isBlank()
                || objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("A committed storage object requires a locator and non-negative size");
        }
        Reservation reservation = lockReservation(tenantId, reservationId);
        if ("COMMITTED".equals(reservation.state())) {
            if (reservation.committedBytes() == actualBytes
                    && Objects.equals(reservation.objectLocator(), objectLocator)
                    && Objects.equals(reservation.objectKey(), objectKey)) {
                return;
            }
            throw StorageQuotaException.invalidReservation("committed object does not match the repeated completion");
        }
        if (!"RESERVED".equals(reservation.state())) {
            throw StorageQuotaException.invalidReservation("cannot commit a " + reservation.state() + " reservation");
        }
        if (actualBytes > reservation.reservedBytes()) {
            throw StorageQuotaException.capacityExceeded("reserved upload size");
        }

        requireOne(jdbc.update("""
                UPDATE storage_tenant_usage
                SET reserved_bytes = reserved_bytes - ?, committed_bytes = committed_bytes + ?,
                    row_version = row_version + 1
                WHERE tenant_id = ? AND reserved_bytes >= ?
                """, reservation.reservedBytes(), actualBytes, tenantId, reservation.reservedBytes()),
                "tenant reservation commit");
        requireOne(jdbc.update("""
                UPDATE storage_provider_quota
                SET reserved_bytes = reserved_bytes - ?, committed_bytes = committed_bytes + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE provider = ? AND reserved_bytes >= ?
                """, reservation.reservedBytes(), actualBytes, reservation.provider(), reservation.reservedBytes()),
                "provider reservation commit");
        requireOne(jdbc.update("""
                UPDATE storage_quota_reservation
                SET state = 'COMMITTED', reserved_bytes = 0, committed_bytes = ?,
                    object_locator = ?, object_key = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND state = 'RESERVED'
                """, actualBytes, objectLocator, objectKey, reservationId, tenantId),
                "reservation commit");
    }

    /**
     * Releases a reservation or committed object exactly once. The caller must invoke this only
     * after the external provider has confirmed deletion, or when a pre-upload reservation is
     * aborted/expired. Repeated cleanup retries are harmless.
     */
    @Transactional
    public void release(UUID tenantId, UUID reservationId) {
        releaseInternal(tenantId, reservationId);
    }

    /** Release from afterCommit/afterCompletion callbacks in a transaction of its own. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseDurable(UUID tenantId, UUID reservationId) {
        releaseInternal(tenantId, reservationId);
    }

    private void releaseInternal(UUID tenantId, UUID reservationId) {
        Reservation reservation = lockReservation(tenantId, reservationId);
        if ("RELEASED".equals(reservation.state())) return;

        long reserved = "RESERVED".equals(reservation.state()) ? reservation.reservedBytes() : 0;
        long committed = "COMMITTED".equals(reservation.state()) ? reservation.committedBytes() : 0;
        requireOne(jdbc.update("""
                UPDATE storage_tenant_usage
                SET reserved_bytes = reserved_bytes - ?, committed_bytes = committed_bytes - ?,
                    row_version = row_version + 1
                WHERE tenant_id = ? AND reserved_bytes >= ? AND committed_bytes >= ?
                """, reserved, committed, tenantId, reserved, committed),
                "tenant reservation release");
        requireOne(jdbc.update("""
                UPDATE storage_provider_quota
                SET reserved_bytes = reserved_bytes - ?, committed_bytes = committed_bytes - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE provider = ? AND reserved_bytes >= ? AND committed_bytes >= ?
                """, reserved, committed, reservation.provider(), reserved, committed),
                "provider reservation release");
        requireOne(jdbc.update("""
                UPDATE storage_quota_reservation
                SET state = 'RELEASED', released_bytes = reserved_bytes + committed_bytes,
                    reserved_bytes = 0, committed_bytes = 0,
                    released_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND state <> 'RELEASED'
                """, reservationId, tenantId),
                "reservation release");
    }

    @Transactional(readOnly = true)
    public List<UsageDrift> reconciliationDrift() {
        return jdbc.query("""
                WITH tenant_ledger AS (
                    SELECT tenant_id,
                           SUM(reserved_bytes) FILTER (WHERE state = 'RESERVED') AS reserved_bytes,
                           SUM(committed_bytes) FILTER (WHERE state = 'COMMITTED') AS committed_bytes
                    FROM storage_quota_reservation
                    WHERE state IN ('RESERVED', 'COMMITTED')
                    GROUP BY tenant_id
                ),
                provider_ledger AS (
                    SELECT provider,
                           SUM(reserved_bytes) FILTER (WHERE state = 'RESERVED') AS reserved_bytes,
                           SUM(committed_bytes) FILTER (WHERE state = 'COMMITTED') AS committed_bytes
                    FROM storage_quota_reservation
                    WHERE state IN ('RESERVED', 'COMMITTED')
                    GROUP BY provider
                )
                SELECT 'TENANT' AS scope, usage.tenant_id::text AS scope_key,
                       usage.reserved_bytes AS aggregate_reserved,
                       COALESCE(ledger.reserved_bytes, 0) AS ledger_reserved,
                       usage.committed_bytes AS aggregate_committed,
                       COALESCE(ledger.committed_bytes, 0) AS ledger_committed
                FROM storage_tenant_usage usage
                LEFT JOIN tenant_ledger ledger ON ledger.tenant_id = usage.tenant_id
                WHERE usage.reserved_bytes <> COALESCE(ledger.reserved_bytes, 0)
                   OR usage.committed_bytes <> COALESCE(ledger.committed_bytes, 0)
                UNION ALL
                SELECT 'PROVIDER', quota.provider,
                       quota.reserved_bytes, COALESCE(ledger.reserved_bytes, 0),
                       quota.committed_bytes, COALESCE(ledger.committed_bytes, 0)
                FROM storage_provider_quota quota
                LEFT JOIN provider_ledger ledger ON ledger.provider = quota.provider
                WHERE quota.reserved_bytes <> COALESCE(ledger.reserved_bytes, 0)
                   OR quota.committed_bytes <> COALESCE(ledger.committed_bytes, 0)
                """, (rs, row) -> new UsageDrift(
                rs.getString("scope"), rs.getString("scope_key"),
                rs.getLong("aggregate_reserved"), rs.getLong("ledger_reserved"),
                rs.getLong("aggregate_committed"), rs.getLong("ledger_committed")));
    }

    private ProviderQuota lockProvider(StorageProviderType provider) {
        List<ProviderQuota> rows = jdbc.query("""
                SELECT writes_enabled, hard_limit_bytes, max_object_bytes, reserved_bytes, committed_bytes
                FROM storage_provider_quota
                WHERE provider = ?
                FOR UPDATE
                """, (rs, row) -> new ProviderQuota(
                rs.getBoolean("writes_enabled"), nullableLong(rs, "hard_limit_bytes"),
                nullableLong(rs, "max_object_bytes"), rs.getLong("reserved_bytes"),
                rs.getLong("committed_bytes")), provider.name());
        if (rows.isEmpty()) throw StorageQuotaException.providerUnavailable(provider.name());
        return rows.getFirst();
    }

    private TenantUsage lockTenant(UUID tenantId) {
        return jdbc.queryForObject("""
                SELECT reserved_bytes, committed_bytes
                FROM storage_tenant_usage
                WHERE tenant_id = ?
                FOR UPDATE
                """, (rs, row) -> new TenantUsage(rs.getLong("reserved_bytes"), rs.getLong("committed_bytes")),
                tenantId);
    }

    private Reservation lockReservation(UUID tenantId, UUID reservationId) {
        List<String> providers = jdbc.query("""
                SELECT provider
                FROM storage_quota_reservation
                WHERE tenant_id = ? AND id = ?
                """, (rs, row) -> rs.getString(1), tenantId, reservationId);
        if (providers.isEmpty()) throw StorageQuotaException.invalidReservation("reservation does not exist");

        // Every mutating operation uses the same lock order: provider account, tenant aggregate,
        // then reservation. This prevents reserve-vs-release deadlocks across concurrent requests.
        lockProvider(StorageProviderType.valueOf(providers.getFirst()));
        lockTenant(tenantId);
        List<Reservation> rows = jdbc.query("""
                SELECT id, provider, state, reserved_bytes, committed_bytes, object_locator, object_key
                FROM storage_quota_reservation
                WHERE tenant_id = ? AND id = ?
                FOR UPDATE
                """, (rs, row) -> new Reservation(
                rs.getObject("id", UUID.class), rs.getString("provider"), rs.getString("state"),
                rs.getLong("reserved_bytes"), rs.getLong("committed_bytes"), rs.getString("object_locator"),
                rs.getString("object_key")),
                tenantId, reservationId);
        if (rows.isEmpty()) throw StorageQuotaException.invalidReservation("reservation does not exist");
        return rows.getFirst();
    }

    private static void requireOne(int rows, String operation) {
        if (rows != 1) throw new IllegalStateException("Storage quota ledger invariant failed during " + operation);
    }

    private static long add(long reserved, long committed, long incoming) {
        try {
            return Math.addExact(Math.addExact(reserved, committed), incoming);
        } catch (ArithmeticException overflow) {
            throw StorageQuotaException.capacityExceeded("byte counter overflow");
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ProviderQuota(boolean writesEnabled, Long hardLimitBytes, Long maxObjectBytes,
                                 long reservedBytes, long committedBytes) {}
    private record TenantUsage(long reservedBytes, long committedBytes) {}
    private record Reservation(UUID id, String provider, String state, long reservedBytes,
                               long committedBytes, String objectLocator, String objectKey) {}
    public record UsageDrift(String scope, String scopeKey, long aggregateReserved, long ledgerReserved,
                             long aggregateCommitted, long ledgerCommitted) {}
}
