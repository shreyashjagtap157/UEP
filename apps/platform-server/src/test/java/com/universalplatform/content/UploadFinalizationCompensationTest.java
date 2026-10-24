package com.universalplatform.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import com.universalplatform.storage.StorageAdapter;
import com.universalplatform.storage.StorageAdapterRegistry;
import com.universalplatform.storage.StorageProviderType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class UploadFinalizationCompensationTest {
    private static final byte[] FILE_BYTES = new byte[]{1, 2, 3, 4};
    private final UUID tenantId = UUID.randomUUID();
    private final UUID uploadId = UUID.randomUUID();
    private final String actor = "upload-owner";

    @TempDir Path tempDir;

    @Mock TenantContext tenants;
    @Mock ActorContext actors;
    @Mock AuthorizationService auth;
    @Mock UploadSessionRepository uploads;
    @Mock StorageObjectRepository objects;
    @Mock StorageAdapterRegistry adapters;
    @Mock AuditService audit;
    @Mock MalwareScanner malwareScanner;
    @Mock StorageCleanupOutboxService cleanupOutbox;
    @Mock StorageAdapter adapter;

    private UploadService service;
    private UploadSession session;
    private Path stagingFile;

    @BeforeEach
    void setUp() throws Exception {
        stagingFile = tempDir.resolve("payload.part");
        Files.write(stagingFile, FILE_BYTES);
        session = new UploadSession(uploadId, tenantId, actor, "report.bin", "application/octet-stream",
                FILE_BYTES.length, null, StorageProviderType.LOCAL, stagingFile.toString(), Instant.now());
        session.receivedBytes = FILE_BYTES.length;
        session.status = UploadStatus.UPLOADING;

        service = new UploadService(tenants, actors, auth, uploads, objects, adapters, audit,
                malwareScanner, cleanupOutbox, tempDir.toString(), 1024);

        when(tenants.requireTenantId()).thenReturn(tenantId);
        when(actors.requireSubject()).thenReturn(actor);
        when(uploads.lock(tenantId, uploadId)).thenReturn(Optional.of(session));
        when(malwareScanner.scan(stagingFile)).thenReturn(ScanStatus.CLEAN);
        when(adapters.require(StorageProviderType.LOCAL)).thenReturn(adapter);
        when(adapter.put(anyString(), any(), eq((long) FILE_BYTES.length), eq("application/octet-stream")))
                .thenReturn("remote-locator");
        when(objects.save(any(StorageObject.class))).thenAnswer(call -> call.getArgument(0));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void rollbackDeletesProviderObjectAndKeepsStagingFileForRetry() throws Exception {
        service.finalizeUpload(uploadId);

        completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(adapter).delete("remote-locator");
        verify(cleanupOutbox, never()).enqueue(any(), any(), anyString(), anyString(), any());
        assertThat(Files.exists(stagingFile)).isTrue();
    }

    @Test
    void rollbackPersistsDurableCleanupWhenProviderCompensationFails() throws Exception {
        doThrow(new IOException("provider failure")).when(adapter).delete("remote-locator");

        service.finalizeUpload(uploadId);
        completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);

        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(cleanupOutbox).enqueue(eq(tenantId), eq(StorageProviderType.LOCAL),
                eq("remote-locator"), objectKey.capture(), any(IOException.class));
        assertThat(objectKey.getValue()).contains(tenantId.toString()).contains("report.bin");
        assertThat(Files.exists(stagingFile)).isTrue();
    }

    @Test
    void commitKeepsProviderObjectAndRemovesStagingFile() throws Exception {
        service.finalizeUpload(uploadId);

        completeSynchronizations(TransactionSynchronization.STATUS_COMMITTED);

        verify(adapter, never()).delete("remote-locator");
        verify(cleanupOutbox, never()).enqueue(any(), any(), anyString(), anyString(), any());
        assertThat(Files.exists(stagingFile)).isFalse();
        assertThat(session.status).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    void auditFailureCompensatesProviderObjectWhenTransactionRollsBack() throws Exception {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).record(eq(tenantId), eq(actor), eq("UPLOAD_COMPLETED"), eq("storage_object"), anyString());

        assertThatThrownBy(() -> service.finalizeUpload(uploadId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit unavailable");
        completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(adapter).delete("remote-locator");
        assertThat(Files.exists(stagingFile)).isTrue();
    }

    private static void completeSynchronizations(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(status));
    }
}
