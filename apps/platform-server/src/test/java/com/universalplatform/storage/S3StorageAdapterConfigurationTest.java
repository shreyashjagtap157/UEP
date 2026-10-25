package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class S3StorageAdapterConfigurationTest {
    @Test
    void rejectsPartialStaticCredentialsRatherThanSilentlyFallingBackToInstanceCredentials() {
        assertThatThrownBy(() -> new S3StorageAdapter(
                URI.create("https://s3.example.test"),
                "us-east-1",
                "explicit-access-key",
                "",
                "education-assets"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must both be set or both be blank");
    }

    @Test
    void rejectsNonHttpEndpointBeforeCreatingTheClient() {
        assertThatThrownBy(() -> new S3StorageAdapter(
                URI.create("file:///tmp/s3"),
                "us-east-1",
                "",
                "",
                "education-assets"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute HTTP(S) URI");
    }

    @Test
    void rejectsBlankBucketBeforeCreatingTheClient() {
        assertThatThrownBy(() -> new S3StorageAdapter(
                URI.create("https://s3.example.test"),
                "us-east-1",
                "",
                "",
                " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket must be configured");
    }
}
