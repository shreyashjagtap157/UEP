package com.universalplatform.liveclass;

/** Provider-neutral media control boundary used by the live-class domain. */
public interface LiveMediaProvider {
    String serverUrl();
    String issueToken(String identity, String name, String room, boolean publish, boolean subscribe,
                      boolean publishData, boolean admin, long ttlSeconds);
}
