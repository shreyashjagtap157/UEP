@org.springframework.modulith.ApplicationModule(
        // Tenancy is a lower-level module that declares no outgoing dependencies. Identity needs it only to
        // resolve the local development tenant (LocalDevelopmentIdentityBootstrapper), the same way Security does.
        displayName = "Identity",
        allowedDependencies = {"security", "audit", "tenancy"}
)
package com.universalplatform.identity;
