package com.universalplatform.messaging;

class MessagingParticipantNotFoundException extends MessagingNotFoundException {
    MessagingParticipantNotFoundException() { super("A participant membership was not found in the authenticated tenant"); }
}
