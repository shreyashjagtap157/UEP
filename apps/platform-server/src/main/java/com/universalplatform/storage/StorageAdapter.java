package com.universalplatform.storage;
import java.io.IOException;
import java.io.InputStream;
public interface StorageAdapter {
    StorageProviderType type();
    String put(String objectKey, InputStream source, long length, String contentType) throws IOException;
    InputStream open(String locator) throws IOException;
    void delete(String locator) throws IOException;
}
