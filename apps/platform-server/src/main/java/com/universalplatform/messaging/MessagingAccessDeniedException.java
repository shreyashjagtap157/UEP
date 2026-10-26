package com.universalplatform.messaging;

class MessagingAccessDeniedException extends RuntimeException {
    MessagingAccessDeniedException() { super("Messaging requires active memberships in the authenticated tenant"); }
}
