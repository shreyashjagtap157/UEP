package com.universalplatform.liveclass;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class LiveKitMediaProvider implements LiveMediaProvider {
    private final String apiKey;
    private final byte[] secret;
    private final String url;

    LiveKitMediaProvider(@Value("${platform.live-learning.livekit.api-key:}") String apiKey,
            @Value("${platform.live-learning.livekit.api-secret:}") String secret,
            @Value("${platform.live-learning.livekit.url:ws://localhost:7880}") String url) {
        this.apiKey = apiKey;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.url = url;
    }

    @Override
    public String serverUrl() {
        return url;
    }

    @Override
    public String issueToken(String identity, String name, String room, boolean publish, boolean subscribe,
            boolean publishData, boolean admin, long ttlSeconds) {
        if (apiKey.isBlank() || secret.length == 0)
            throw new IllegalStateException("LiveKit credentials are not configured");
        long now = Instant.now().getEpochSecond();
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String claims = b64("{\"iss\":\"" + json(apiKey) + "\",\"sub\":\"" + json(identity) + "\",\"name\":\""
                + json(name) + "\",\"nbf\":" + now + ",\"exp\":" + (now + ttlSeconds) + ",\"video\":{" +
                "\"room\":\"" + json(room) + "\",\"roomJoin\":true,\"canPublish\":" + publish + ",\"canSubscribe\":"
                + subscribe + ",\"canPublishData\":" + publishData + ",\"roomAdmin\":" + admin + "}}");
        String signing = header + "." + claims;
        return signing + "." + b64(hmac(signing));
    }

    private String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    private String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private byte[] hmac(String s) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign LiveKit token", e);
        }
    }
}
