package com.universalplatform.storage;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
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

    @Autowired
    S3StorageAdapter(@Value("${platform.storage.s3.endpoint}") URI endpoint,
                     @Value("${platform.storage.s3.region:us-east-1}") String region,
                     @Value("${platform.storage.s3.access-key:}") String accessKey,
                     @Value("${platform.storage.s3.secret-key:}") String secretKey,
                     @Value("${platform.storage.s3.bucket}") String bucket,
                     @Value("${platform.development-mode:false}") boolean developmentMode) {
        if (endpoint == null || endpoint.getHost() == null
                || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("S3 endpoint must be an absolute HTTP(S) URI");
        }
        if (!developmentMode && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("S3 endpoint must use HTTPS outside development mode");
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
        builder.credentialsProvider(accessKeyMissing || secretKeyMissing
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        this.client = builder.build();
    }

    // Preserve a concise production-mode constructor for focused adapter tests.
    S3StorageAdapter(URI endpoint, String region, String accessKey, String secretKey, String bucket) {
        this(endpoint, region, accessKey, secretKey, bucket, false);
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.S3_COMPATIBLE;
    }

    @Override
    public String put(String key, InputStream source, long length, String contentType) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("S3 object key must not be blank");
        }
        if (source == null || length < 0) {
            throw new IOException("S3 object stream and non-negative content length are required");
        }
        String requestedContentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType;
        if (requestedContentType.chars().anyMatch(ch -> ch <= 0x1f || ch == 0x7f)) {
            throw new IOException("S3 object content type is invalid");
        }
        final String validatedContentType;
        try {
            validatedContentType = MediaType.parseMediaType(requestedContentType).toString();
        } catch (IllegalArgumentException invalidContentType) {
            throw new IOException("S3 object content type is invalid");
        }
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(validatedContentType)
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
