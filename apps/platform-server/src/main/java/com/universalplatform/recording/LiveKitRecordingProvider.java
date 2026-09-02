package com.universalplatform.recording;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class LiveKitRecordingProvider implements RecordingMediaProvider {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private final ObjectMapper json=new ObjectMapper(); private final String apiKey; private final byte[] secret; private final String apiUrl;
    LiveKitRecordingProvider(@Value("${platform.live-learning.livekit.api-key:}")String key,@Value("${platform.live-learning.livekit.api-secret:}")String sec,
                             @Value("${platform.live-learning.livekit.api-url:http://localhost:7880}")String apiUrl){this.apiKey=key;this.secret=sec.getBytes(StandardCharsets.UTF_8);this.apiUrl=apiUrl.replaceAll("/$","");}
    @Override public StartResult startRoomComposite(String roomName,RecordingQualityPreset quality,String outputPath){
        String body="{\"room_name\":\""+esc(roomName)+"\",\"layout\":\"speaker\",\"file_outputs\":[{\"file_type\":\"MP4\",\"filepath\":\""+esc(outputPath)+"\",\"disable_manifest\":true}],\"preset\":\""+preset(quality)+"\"}";
        JsonNode n=post("StartRoomCompositeEgress",body); return new StartResult(n.path("egress_id").asText(),n.path("status").asText());
    }
    @Override public void stop(String id){post("StopEgress","{\"egress_id\":\""+esc(id)+"\"}");}
    @Override public EgressState status(String id){
        JsonNode root=post("ListEgress","{\"egress_id\":\""+esc(id)+"\"}"); JsonNode n=root.path("items").isArray()&&root.path("items").size()>0?root.path("items").get(0):root;
        String status=n.path("status").asText(); String file=""; long duration=0;
        JsonNode files=n.path("file_results"); if(files.isArray()&&files.size()>0){JsonNode f=files.get(0);file=f.path("filename").asText();duration=f.path("duration").asLong();}
        return new EgressState(id,status,file,duration,n.path("error").asText(null));
    }
    private JsonNode post(String method,String body){
        try{HttpRequest r=HttpRequest.newBuilder(URI.create(apiUrl+"/twirp/livekit.Egress/"+method)).header("Authorization","Bearer "+roomRecordToken()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();HttpResponse<String> response=http.send(r,HttpResponse.BodyHandlers.ofString());if(response.statusCode()/100!=2)throw new IllegalStateException("LiveKit Egress "+method+" failed: HTTP "+response.statusCode()+" "+response.body());return json.readTree(response.body());}
        catch(Exception e){throw new IllegalStateException("LiveKit Egress request failed",e);}
    }
    private String roomRecordToken(){long now=Instant.now().getEpochSecond();String h=b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");String c=b64("{\"iss\":\""+esc(apiKey)+"\",\"sub\":\"uep-recording-worker\",\"nbf\":"+now+",\"exp\":"+(now+300)+",\"video\":{\"roomRecord\":true}}");String s=h+"."+c;return s+"."+b64(hmac(s));}
    private String preset(RecordingQualityPreset q){return RecordingQualityMapper.liveKitPreset(q);}
    private String b64(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private byte[] hmac(String s){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret,"HmacSHA256"));return m.doFinal(s.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"");}
}
