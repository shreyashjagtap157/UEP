package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class S3StorageAdapterHttpIntegrationTest {
    @Test
    void writesReadsAndDeletesAnObjectThroughTheS3CompatibleHttpContract() throws Exception {
        AtomicReference<byte[]> object = new AtomicReference<>();
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            requests.add(method + " " + path);
            try {
                switch (method) {
                    case "PUT" -> {
                        object.set(exchange.getRequestBody().readAllBytes());
                        exchange.getResponseHeaders().add("ETag", "\"uep-test-etag\"");
                        exchange.sendResponseHeaders(200, -1);
                    }
                    case "GET" -> {
                        byte[] bytes = object.get();
                        if (bytes == null) {
                            byte[] error = "<Error><Code>NoSuchKey</Code></Error>".getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(404, error.length);
                            exchange.getResponseBody().write(error);
                        } else {
                            exchange.getResponseHeaders().add("Content-Type", "text/plain");
                            exchange.sendResponseHeaders(200, bytes.length);
                            exchange.getResponseBody().write(bytes);
                        }
                    }
                    case "DELETE" -> {
                        object.set(null);
                        exchange.sendResponseHeaders(204, -1);
                    }
                    default -> exchange.sendResponseHeaders(405, -1);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();

        S3StorageAdapter adapter = null;
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            adapter = new S3StorageAdapter(
                    URI.create(endpoint),
                    "us-east-1",
                    "test-access-key",
                    "test-secret-key",
                    "uep-test-bucket",
                    true);
            byte[] payload = "UEP durable object test payload".getBytes(StandardCharsets.UTF_8);

            String locator = adapter.put(
                    "tenant-a/content/qualification.txt",
                    new java.io.ByteArrayInputStream(payload),
                    payload.length,
                    "text/plain");
            assertThat(locator).isEqualTo("tenant-a/content/qualification.txt");

            try (var downloaded = adapter.open(locator)) {
                assertThat(downloaded.readAllBytes()).containsExactly(payload);
            }

            adapter.delete(locator);

            assertThat(object.get()).isNull();
            assertThat(requests).containsExactly(
                    "PUT /uep-test-bucket/tenant-a/content/qualification.txt",
                    "GET /uep-test-bucket/tenant-a/content/qualification.txt",
                    "DELETE /uep-test-bucket/tenant-a/content/qualification.txt");
        } finally {
            if (adapter != null) adapter.close();
            server.stop(0);
        }
    }
}
