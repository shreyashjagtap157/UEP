package com.universalplatform.messaging;

class MessagingDisabledException extends RuntimeException {
    MessagingDisabledException() {
        super("Messaging is disabled for this tenant");
    }
}
