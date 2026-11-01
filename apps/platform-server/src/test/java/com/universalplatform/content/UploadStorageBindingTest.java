package com.universalplatform.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import com.universalplatform.storage.TenantStorageBinding;
import com.universalplatform.storage.TenantStorageBindingResolver;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UploadStorageBindingTest {
    private static final UUID TENANT = UUID.fromString("01901234-4444-7000-8000-000000000077");

    @TempDir Path staging;

    @Mock TenantContext tenants;
    @Mock ActorContext actors;
    @Mock AuthorizationService auth;
    @Mock UploadSessionRepository uploads;
    @Mock StorageObjectRepository objects;
    @Mock StorageAdapterRegistry adapters;
    @Mock TenantStorageBindingResolver bindings;
    @Mock AuditService audit;
    @Mock MalwareScanner scanner;
    @Mock StorageCleanupOutboxService cleanup;
    @Mock UploadStagingCleanupService stagingCleanup;
    @Mock StorageAdapter adapter;

    @Test
    void uploadUsesTenantConfiguredS3ProviderAndSnapshotsPrefix() throws Exception {
        UploadService service = service(false);
        when(bindings.findByTenantId(TENANT))
                .thenReturn(Optional.of(new TenantStorageBinding(StorageProviderType.S3_COMPATIBLE, "course-assets")));
        when(adapters.require(StorageProviderType.S3_COMPATIBLE)).thenReturn(adapter);
        when(uploads.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadService.UploadView result = service.initiate("lesson.pdf", "application/pdf", 12, null);

        ArgumentCaptor<UploadSession> session = ArgumentCaptor.forClass(UploadSession.class);
        verify(uploads).save(session.capture());
        assertThat(result.provider()).isEqualTo(StorageProviderType.S3_COMPATIBLE);
        assertThat(session.getValue().provider).isEqualTo(StorageProviderType.S3_COMPATIBLE);
        assertThat(session.getValue().objectPrefix).isEqualTo("course-assets");
    }

    @Test
    void productionUploadFailsClosedWhenTenantHasNoExternalStorageBinding() throws Exception {
        UploadService service = service(false);
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate("lesson.pdf", "application/pdf", 12, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void localProviderFallbackIsAvailableOnlyInDevelopmentMode() throws Exception {
        UploadService service = service(true);
        when(bindings.findByTenantId(TENANT)).thenReturn(Optional.empty());
        when(adapters.require(StorageProviderType.LOCAL)).thenReturn(adapter);
        when(uploads.save(any(UploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadService.UploadView result = service.initiate("lesson.pdf", "application/pdf", 12, null);

        assertThat(result.provider()).isEqualTo(StorageProviderType.LOCAL);
    }

    private UploadService service(boolean developmentMode) {
        when(tenants.requireTenantId()).thenReturn(TENANT);
        when(actors.requireSubject()).thenReturn("staging-owner");
        return new UploadService(tenants, actors, auth, uploads, objects, adapters, bindings, audit,
                scanner, cleanup, stagingCleanup, staging.toString(), 1024, developmentMode);
    }
}
