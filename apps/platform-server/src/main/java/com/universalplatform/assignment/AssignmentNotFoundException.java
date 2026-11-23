package com.universalplatform.assignment;

/**
 * Indicates that an assignment is not visible in the current tenant context.
 * This intentionally covers both absent IDs and IDs owned by another tenant.
 */
final class AssignmentNotFoundException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    AssignmentNotFoundException() {
        super("Assignment not found");
    }
}
