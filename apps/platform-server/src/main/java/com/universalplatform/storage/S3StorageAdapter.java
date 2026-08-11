package com.universalplatform.storage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
@Component
@ConditionalOnProperty(name="platform.storage.s3.enabled", havingValue="true")
class S3StorageAdapter implements StorageAdapter {
    private final S3Client client; private final String bucket;
    S3StorageAdapter(@Value("${platform.storage.s3.endpoint}") URI endpoint,
                     @Value("${platform.storage.s3.region:us-east-1}") String region,
                     @Value("${platform.storage.s3.access-key}") String accessKey,
                     @Value("${platform.storage.s3.secret-key}") String secretKey,
                     @Value("${platform.storage.s3.bucket}") String bucket) {
        this.bucket=bucket;
        this.client=S3Client.builder().endpointOverride(endpoint).region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true).build();
    }
    public StorageProviderType type(){return StorageProviderType.S3_COMPATIBLE;}
    public String put(String key, InputStream source, long length, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromInputStream(source,length)); return key;
    }
    public InputStream open(String locator) { ResponseInputStream<GetObjectResponse> in=client.getObject(GetObjectRequest.builder().bucket(bucket).key(locator).build()); return in; }
    public void delete(String locator) { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(locator).build()); }
}
