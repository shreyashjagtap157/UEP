package com.universalplatform.storage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
class LocalStorageAdapter implements StorageAdapter {
    private final Path root;
    LocalStorageAdapter(@Value("${platform.storage.local.root:./var/storage}") String root) { this.root = Path.of(root).toAbsolutePath().normalize(); }
    public StorageProviderType type() { return StorageProviderType.LOCAL; }
    public String put(String objectKey, InputStream source, long length, String contentType) throws IOException {
        Path target = safe(objectKey); Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".part");
        try (source) { Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING); }
        if (Files.size(tmp) != length) { Files.deleteIfExists(tmp); throw new IOException("Storage length mismatch"); }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); return objectKey;
    }
    public InputStream open(String locator) throws IOException { return Files.newInputStream(safe(locator)); }
    public void delete(String locator) throws IOException { Files.deleteIfExists(safe(locator)); }
    private Path safe(String key) { Path p = root.resolve(key).normalize(); if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage key"); return p; }
}
