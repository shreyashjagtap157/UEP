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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleDriveStorageAdapterSharedDriveTest {
    private final HttpClient http = mock(HttpClient.class);

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
