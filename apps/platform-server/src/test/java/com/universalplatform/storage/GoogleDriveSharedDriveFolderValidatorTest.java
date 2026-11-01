package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleDriveSharedDriveFolderValidatorTest {
    private final HttpClient http = mock(HttpClient.class);
    private final GoogleDriveSharedDriveFolderValidator validator =
            new GoogleDriveSharedDriveFolderValidator(http, new ObjectMapper());

    @Test
    void acceptsOnlyTheConfiguredFolderInTheConfiguredSharedDrive() throws Exception {
        respond(200, "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\",\"driveId\":\"drive-456\"}");

        validator.validate("folder-123", "drive-456", "access-token");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().uri().toString())
                .isEqualTo("https://www.googleapis.com/drive/v3/files/folder-123?fields=id%2CmimeType%2CdriveId&supportsAllDrives=true");
        assertThat(request.getValue().headers().firstValue("Authorization")).hasValue("Bearer access-token");
    }

    @Test
    void rejectsFolderFromAnotherSharedDrive() throws Exception {
        respond(200, "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\",\"driveId\":\"different-drive\"}");

        assertThatThrownBy(() -> validator.validate("folder-123", "drive-456", "access-token"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a folder inside the configured Shared Drive");
    }

    @Test
    void rejectsMyDriveFolderWithoutSharedDriveId() throws Exception {
        respond(200, "{\"id\":\"folder-123\",\"mimeType\":\"application/vnd.google-apps.folder\"}");

        assertThatThrownBy(() -> validator.validate("folder-123", "drive-456", "access-token"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a folder inside the configured Shared Drive");
    }

    @Test
    void rejectsNonFolderItems() throws Exception {
        respond(200, "{\"id\":\"file-123\",\"mimeType\":\"application/pdf\",\"driveId\":\"drive-456\"}");

        assertThatThrownBy(() -> validator.validate("folder-123", "drive-456", "access-token"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a folder inside the configured Shared Drive");
    }

    @Test
    void failsClosedWhenDriveCannotResolveTheFolder() throws Exception {
        respond(404, "{\"error\":\"notFound\"}");

        assertThatThrownBy(() -> validator.validate("folder-123", "drive-456", "access-token"))
                .isInstanceOf(IOException.class)
                .hasMessage("Google Shared Drive folder validation failed: HTTP 404");
    }

    @Test
    void rejectsMissingFolderOrDriveIdentifiersBeforeMakingARequest() {
        assertThatThrownBy(() -> validator.validate(" ", "drive-456", "access-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void respond(int status, String body) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
    }
}
