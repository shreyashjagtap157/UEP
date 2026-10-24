package com.universalplatform.content;

/** Terminal upload rejection that must be returned to the client without rolling back the ABORTED state. */
final class UploadRejectedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    UploadRejectedException(String message) {
        super(message);
    }
}
