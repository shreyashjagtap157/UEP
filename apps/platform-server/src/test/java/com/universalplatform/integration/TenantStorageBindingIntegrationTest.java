package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantStorageBindingIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-4444-7000-8000-000000000077");

    @Mock TenantContext tenants;
    @Mock AuthorizationService auth;
    @Mock StorageAdapterRegistry adapters;
    @Mock StorageAdapter adapter;
    @Mock ExternalStorageBindingRepository bindings;

    @BeforeEach
    void setUp() {
        lenient().when(tenants.requireTenantId()).thenReturn(TENANT);
        lenient().when(adapters.require(any())).thenReturn(adapter);
    }

    @Test
    void resolverReturnsOnlyTheRequestedTenantBinding() {
        ExternalStorageBinding binding = new ExternalStorageBinding(
                UUID.randomUUID(), TENANT, StorageProviderType.S3_COMPATIBLE.name(), "course-assets", Instant.EPOCH);
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.of(binding));

        var resolved = new DatabaseTenantStorageBindingResolver(bindings).findByTenantId(TENANT);

        assertThat(resolved).contains(new com.universalplatform.storage.TenantStorageBinding(
                StorageProviderType.S3_COMPATIBLE, "course-assets"));
        verify(bindings).findByTenantId(TENANT);
    }

    @Test
    void resolverPreservesAnUnconfiguredTenantAsEmpty() {
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThat(new DatabaseTenantStorageBindingResolver(bindings).findByTenantId(TENANT)).isEmpty();
    }

    @Test
    void newBindingNormalizesPrefixAndPersistsAgainstCurrentTenant() {
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.empty());
        when(bindings.save(any(ExternalStorageBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(false).configure(StorageProviderType.S3_COMPATIBLE, "  course-assets///  ", 0);

        assertThat(result.providerType()).isEqualTo(StorageProviderType.S3_COMPATIBLE);
        assertThat(result.objectPrefix()).isEqualTo("course-assets");
        verify(bindings).save(any(ExternalStorageBinding.class));
        verify(auth).require(PermissionKey.EXTERNAL_STORAGE_MANAGE);
        verify(tenants).requireTenantId();
    }

    @Test
    void existingBindingCanBeChangedOnlyAtItsExpectedVersion() {
        ExternalStorageBinding existing = new ExternalStorageBinding(
                UUID.randomUUID(), TENANT, StorageProviderType.S3_COMPATIBLE.name(), "old-prefix", Instant.EPOCH);
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.of(existing));

        var result = service(false).configure(StorageProviderType.GOOGLE_DRIVE, "new-prefix", 0);

        assertThat(result.providerType()).isEqualTo(StorageProviderType.GOOGLE_DRIVE);
        assertThat(result.objectPrefix()).isEqualTo("new-prefix");
        assertThat(existing.providerType()).isEqualTo(StorageProviderType.GOOGLE_DRIVE.name());
        assertThat(existing.objectPrefix()).isEqualTo("new-prefix");
        verify(bindings, never()).save(any(ExternalStorageBinding.class));
    }

    @Test
    void staleExpectedVersionDoesNotMutateExistingBinding() {
        ExternalStorageBinding existing = new ExternalStorageBinding(
                UUID.randomUUID(), TENANT, StorageProviderType.S3_COMPATIBLE.name(), "old-prefix", Instant.EPOCH);
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service(false).configure(StorageProviderType.GOOGLE_DRIVE, "new-prefix", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modified by another request");

        assertThat(existing.providerType()).isEqualTo(StorageProviderType.S3_COMPATIBLE.name());
        assertThat(existing.objectPrefix()).isEqualTo("old-prefix");
    }

    @Test
    void localFilesystemCannotBeConfiguredOutsideDevelopmentMode() {
        assertThatThrownBy(() -> service(false).configure(StorageProviderType.LOCAL, "", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("development mode");

        verify(bindings, never()).findByTenantId(any());
    }

    @Test
    void rejectsTraversalAndNonPortableObjectPrefixes() {
        for (String prefix : new String[] {"../escape", "a/../b", "/absolute", "a\\b", "a//b"}) {
            assertThatThrownBy(() -> service(false).configure(StorageProviderType.S3_COMPATIBLE, prefix, 0))
                    .as("prefix %s", prefix)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid object prefix");
        }
    }

    @Test
    void currentBindingRequiresStorageManagementPermission() {
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThat(service(false).current()).isNull();

        verify(auth).require(PermissionKey.EXTERNAL_STORAGE_MANAGE);
        verify(tenants).requireTenantId();
    }

    private StorageIntegrationService service(boolean developmentMode) {
        return new StorageIntegrationService(tenants, auth, adapters, bindings, developmentMode);
    }
}
