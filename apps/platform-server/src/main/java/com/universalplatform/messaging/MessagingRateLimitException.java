package com.universalplatform.messaging;

class MessagingRateLimitException extends RuntimeException {
    MessagingRateLimitException() {
        super("Messaging rate limit exceeded; wait before retrying");
    }
}
