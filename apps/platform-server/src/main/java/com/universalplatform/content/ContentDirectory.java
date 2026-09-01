package com.universalplatform.content;
import java.util.UUID;
public interface ContentDirectory {
    void requireReadableResource(UUID resourceId);
}
