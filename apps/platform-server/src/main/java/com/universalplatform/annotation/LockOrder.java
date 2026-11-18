package com.universalplatform.annotation;

/**
 * Annotation used to document the lock ordering invariant for methods that acquire
 * database locks. The value describes the expected order, e.g. "TenantFirst".
 * Static analysis or CI steps can verify the presence and correctness of this
 * annotation on methods that touch tenant and outbox locks.
 */
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LockOrder {
    /**
     * Describes the expected lock ordering invariant.
     * Example values: "TenantFirst", "OutboxFirst", "None".
     */
    String value();
}