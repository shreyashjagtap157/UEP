package com.universalplatform.security;

public final class MissingTenantContextException extends RuntimeException {
    public MissingTenantContextException(String message) {
        super(message);
    }
}
