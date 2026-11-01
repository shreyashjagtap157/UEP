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
    void rejectsContentTypeHeaderInjectionBeforeSendingAnyProviderRequest() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        S3StorageAdapter adapter = null;
        try {
            adapter = new S3StorageAdapter(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "us-east-1",
                    "test-access-key",
                    "test-secret-key",
                    "uep-test-bucket",
                    true);
            S3StorageAdapter finalAdapter = adapter;
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> finalAdapter.put(
                    "tenant-a/content/unsafe.txt",
                    new java.io.ByteArrayInputStream(new byte[] {1}),
                    1,
                    "text/plain\r\nX-Injected: true"))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("content type is invalid");
            assertThat(requests).isEmpty();
        } finally {
            if (adapter != null) adapter.close();
            server.stop(0);
        }
    }

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
                        object.set(readS3Body(exchange));
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

    /**
     * AWS SDK v2 can use the aws-chunked content encoding for streaming S3 uploads.
     * HttpServer removes HTTP transfer framing, but not this S3 signing layer.
     * Decode it here so the fake endpoint asserts the actual object bytes.
     */
    private static byte[] readS3Body(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
        byte[] encoded = exchange.getRequestBody().readAllBytes();
        String contentEncoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        if (contentEncoding == null || !java.util.Arrays.stream(contentEncoding.split(","))
                .anyMatch(value -> value.trim().equalsIgnoreCase("aws-chunked"))) {
            return encoded;
        }

        try (var input = new java.io.ByteArrayInputStream(encoded);
                var decoded = new java.io.ByteArrayOutputStream()) {
            while (true) {
                String chunkHeader = readAsciiLine(input);
                String sizeText = chunkHeader.split(";", 2)[0].trim();
                final int chunkSize;
                try {
                    chunkSize = Integer.parseUnsignedInt(sizeText, 16);
                } catch (NumberFormatException invalidSize) {
                    throw new java.io.IOException("Invalid aws-chunked size", invalidSize);
                }

                if (chunkSize == 0) {
                    String trailer;
                    do {
                        trailer = readAsciiLine(input);
                    } while (!trailer.isEmpty());
                    if (input.available() != 0) {
                        throw new java.io.IOException("Unexpected bytes after aws-chunked trailers");
                    }
                    return decoded.toByteArray();
                }

                byte[] chunk = input.readNBytes(chunkSize);
                if (chunk.length != chunkSize) {
                    throw new java.io.EOFException("Truncated aws-chunked data");
                }
                decoded.write(chunk);
                if (input.read() != '\\r' || input.read() != '\\n') {
                    throw new java.io.IOException("Invalid aws-chunked data terminator");
                }
            }
        }
    }

    private static String readAsciiLine(java.io.InputStream input) throws java.io.IOException {
        var line = new java.io.ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new java.io.EOFException("Unexpected end of aws-chunked stream");
            }
            if (value == '\\r') {
                if (input.read() != '\\n') {
                    throw new java.io.IOException("Invalid aws-chunked line terminator");
                }
                return line.toString(StandardCharsets.US_ASCII);
            }
            line.write(value);
        }
    }

}
