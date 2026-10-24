package com.universalplatform.credential;

class CredentialNotFoundException extends RuntimeException {
    CredentialNotFoundException(String message) {
        super(message);
    }
}
