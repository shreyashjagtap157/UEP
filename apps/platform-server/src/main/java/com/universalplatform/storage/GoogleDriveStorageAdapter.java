package com.universalplatform.storage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="platform.storage.google-drive.enabled", havingValue="true")
class GoogleDriveStorageAdapter implements StorageAdapter {
    private final HttpClient http=HttpClient.newHttpClient(); private final ObjectMapper json=new ObjectMapper();
    private final String token; private final String folderId; private final String sharedDriveId;
    GoogleDriveStorageAdapter(@Value("${platform.storage.google-drive.access-token}") String token,
                              @Value("${platform.storage.google-drive.folder-id:}") String folderId,
                              @Value("${platform.storage.google-drive.shared-drive-id:}") String sharedDriveId) {
        this.token=token; this.folderId=folderId; this.sharedDriveId=sharedDriveId;
    }
    public StorageProviderType type(){ return sharedDriveId.isBlank()?StorageProviderType.GOOGLE_DRIVE:StorageProviderType.GOOGLE_SHARED_DRIVE; }
    public String put(String key, InputStream source, long length, String contentType) throws IOException {
        try {
            String metadata="{\"name\":\""+escape(key)+"\""+(folderId.isBlank()?"":",\"parents\":[\""+escape(folderId)+"\"]")+"}";
            String boundary="uep-boundary"; byte[] head=("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+metadata+"\r\n--"+boundary+"\r\nContent-Type: "+contentType+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] tail=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest.BodyPublisher body=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(head),HttpRequest.BodyPublishers.ofInputStream(()->source),HttpRequest.BodyPublishers.ofByteArray(tail));
            String uri="https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true";
            HttpRequest req=HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token).header("Content-Type","multipart/related; boundary="+boundary).POST(body).build();
            HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString()); if(r.statusCode()/100!=2) throw new IOException("Google Drive upload failed: HTTP "+r.statusCode());
            JsonNode n=json.readTree(r.body()); return n.path("id").asText();
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Google Drive upload interrupted",e);}
    }
    public InputStream open(String id) throws IOException { try { HttpRequest q=HttpRequest.newBuilder(URI.create("https://www.googleapis.com/drive/v3/files/"+id+"?alt=media&supportsAllDrives=true")).header("Authorization","Bearer "+token).GET().build(); HttpResponse<InputStream> r=http.send(q,HttpResponse.BodyHandlers.ofInputStream()); if(r.statusCode()/100!=2) throw new IOException("Google Drive download failed: HTTP "+r.statusCode()); return r.body(); } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);} }
    public void delete(String id) throws IOException { try { HttpRequest q=HttpRequest.newBuilder(URI.create("https://www.googleapis.com/drive/v3/files/"+id+"?supportsAllDrives=true")).header("Authorization","Bearer "+token).DELETE().build(); HttpResponse<Void> r=http.send(q,HttpResponse.BodyHandlers.discarding()); if(r.statusCode()/100!=2) throw new IOException("Google Drive delete failed: HTTP "+r.statusCode()); } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);} }
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}
