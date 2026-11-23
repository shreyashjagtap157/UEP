package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleDriveStorageAdapterSharedDriveTest {
    private final HttpClient http = mock(HttpClient.class);

    @Test
    void multipartBoundaryCannotBeInjectedByFileContents() throws Exception {
        HttpResponse<String> folderResponse = response(200,
                "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\",\"driveId\":\"drive-456\"}");
        HttpResponse<String> uploadResponse = response(200, "{\"id\":\"uploaded-file-789\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(folderResponse, uploadResponse);
        var adapter = adapter();
        String payload = "prefix\r\n--uep-boundary--\r\nsuffix";

        adapter.put("tenant-object-1",
                new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
                payload.getBytes(StandardCharsets.UTF_8).length, "application/octet-stream");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        HttpRequest upload = requests.getAllValues().get(1);
        String contentType = upload.headers().firstValue("Content-Type").orElseThrow();
        assertThat(contentType).contains("boundary=uep-");
        assertThat(contentType).doesNotContain("boundary=uep-boundary");
        assertThat(readBody(upload)).contains(payload);
    }

    @Test
    void rejectsWrongSharedDriveBeforeSendingAnyUploadRequest() throws Exception {
        HttpResponse<String> folderResponse = response(200,
                "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\",\"driveId\":\"other-drive\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(folderResponse);
        var adapter = adapter();

        assertThatThrownBy(() -> adapter.put("tenant-object-1",
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7, "application/octet-stream"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("not a folder inside the configured Shared Drive");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(1)).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/drive/v3/files/folder-123");
    }

    @Test
    void rejectsContentTypeHeaderInjectionBeforeCallingGoogle() {
        var adapter = adapter();

        assertThatThrownBy(() -> adapter.put("tenant-object-1",
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7, "application/octet-stream\r\nX-Injected: true"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("content type is invalid");

        verifyNoInteractions(http);
    }

    @Test
    void validatesFolderThenUploadsToTheConfiguredSharedDriveParent() throws Exception {
        HttpResponse<String> folderResponse = response(200,
                "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\",\"driveId\":\"drive-456\"}");
        HttpResponse<String> uploadResponse = response(200, "{\"id\":\"uploaded-file-789\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(folderResponse, uploadResponse);
        var adapter = adapter();

        assertThat(adapter.put("tenant-object-1",
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7, "application/octet-stream"))
                .isEqualTo("uploaded-file-789");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertThat(requests.getAllValues()).extracting(HttpRequest::method).containsExactly("GET", "POST");
        assertThat(requests.getAllValues().get(1).uri().toString())
                .contains("supportsAllDrives=true");
    }

    @Test
    void serializesObjectNamesAsJsonInsteadOfInterpolatingUntrustedText() throws Exception {
        HttpResponse<String> folderResponse = response(200,
                "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\",\"driveId\":\"drive-456\"}");
        HttpResponse<String> uploadResponse = response(200, "{\"id\":\"uploaded-file-789\"}");
        when(http.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(folderResponse, uploadResponse);
        var adapter = adapter();
        String objectName = "tenant-object\n\"quoted\"";

        adapter.put(objectName,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                7, "application/octet-stream");

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        String multipart = readBody(requests.getAllValues().get(1));
        assertThat(multipart).contains("\"name\":\"tenant-object\\n\\\"quoted\\\"\"");
        assertThat(multipart).doesNotContain("tenant-object\n");
    }

    private static String readBody(HttpRequest request) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<String> completed = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable failure) {
                completed.completeExceptionally(failure);
            }

            @Override
            public void onComplete() {
                completed.complete(output.toString(StandardCharsets.UTF_8));
            }
        });
        return completed.get(5, TimeUnit.SECONDS);
    }

    private GoogleDriveStorageAdapter adapter() {
        return new GoogleDriveStorageAdapter(http, new ObjectMapper(),
                "development-token", "", "", "", "folder-123", "drive-456", true);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
