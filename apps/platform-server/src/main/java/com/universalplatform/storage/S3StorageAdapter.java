package com.universalplatform.storage;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "platform.storage.s3.enabled", havingValue = "true")
class S3StorageAdapter implements StorageAdapter {
    private final S3Client client;
    private final String bucket;

    S3StorageAdapter(@Value("${platform.storage.s3.endpoint}") URI endpoint,
                     @Value("${platform.storage.s3.region:us-east-1}") String region,
                     @Value("${platform.storage.s3.access-key}") String accessKey,
                     @Value("${platform.storage.s3.secret-key}") String secretKey,
                     @Value("${platform.storage.s3.bucket}") String bucket) {
        if (endpoint == null || endpoint.getHost() == null
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("S3 endpoint must be an absolute HTTP(S) URI");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket must be configured");
        }
        boolean accessKeyMissing = accessKey == null || accessKey.isBlank();
        boolean secretKeyMissing = secretKey == null || secretKey.isBlank();
        if (accessKeyMissing != secretKeyMissing) {
            throw new IllegalArgumentException(
                    "S3 access-key and secret-key must both be set or both be blank to use the default credential chain");
        }
        this.bucket = bucket;
        var builder = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .forcePathStyle(true)
                // Bound connection establishment and idle socket waits so workers cannot hang forever.
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofMinutes(5))
                        .maxConnections(50))
                // Leave room for large object uploads while keeping calls bounded.
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofMinutes(10))
                        .apiCallTimeout(Duration.ofMinutes(30))
                        .build());
        builder.credentialsProvider(accessKey.isBlank() || secretKey.isBlank()
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        this.client = builder.build();
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.S3_COMPATIBLE;
    }

    @Override
    public String put(String key, InputStream source, long length, String contentType) {
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromInputStream(source, length));
        return key;
    }

    @Override
    public InputStream open(String locator) {
        ResponseInputStream<GetObjectResponse> response = client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(locator).build());
        return response;
    }

    @Override
    public void delete(String locator) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(locator).build());
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
