package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GoogleDriveStorageAdapterConfigurationTest {
    @Test
    void sharedDriveRequiresAnExplicitDestinationFolder() {
        assertThatThrownBy(() -> new GoogleDriveStorageAdapter("access-token", "", "shared-drive-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a folder ID inside the configured shared drive");
    }

    @Test
    void whitespaceFolderDoesNotSilentlyRouteSharedDriveUploadsToMyDriveRoot() {
        assertThatThrownBy(() -> new GoogleDriveStorageAdapter("access-token", "   ", "shared-drive-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a folder ID inside the configured shared drive");
    }

    @Test
    void configuredSharedDriveUsesTheSharedDriveProviderType() {
        var adapter = new GoogleDriveStorageAdapter("access-token", " shared-folder-123 ", " shared-drive-123 ");

        assertThat(adapter.type()).isEqualTo(StorageProviderType.GOOGLE_SHARED_DRIVE);
    }

    @Test
    void driveWithoutSharedDriveIdRemainsTheMyDriveProvider() {
        var adapter = new GoogleDriveStorageAdapter("access-token", "", "");

        assertThat(adapter.type()).isEqualTo(StorageProviderType.GOOGLE_DRIVE);
    }
}
