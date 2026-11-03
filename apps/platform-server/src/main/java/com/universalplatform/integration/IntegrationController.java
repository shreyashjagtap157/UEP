package com.universalplatform.integration;

import com.universalplatform.identity.FederationIdentityDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ApiCredentialService;
import com.universalplatform.storage.StorageProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Integrations", description = "Endpoints for API credentials, webhooks, storage configurations, identity providers, and notification providers")
@RestController
@RequestMapping("/api/v1/integrations")
class IntegrationController {
    private final ApiCredentialService credentials;
    private final WebhookService webhooks;
    private final StorageIntegrationService storage;
    private final FederationService federation;
    private final ExternalNotificationService notifications;

    IntegrationController(ApiCredentialService credentials, WebhookService webhooks, StorageIntegrationService storage,
            FederationService federation, ExternalNotificationService notifications) {
        this.credentials = credentials;
        this.webhooks = webhooks;
        this.storage = storage;
        this.federation = federation;
        this.notifications = notifications;
    }

    @Operation(
            summary = "Create API credential",
            description = "Creates a new API credential token for a membership. Requires membershipId, name, scopes, and optional expiresAt in the request body."
    )
    @PostMapping("/api-credentials")
    ApiCredentialService.CredentialView createCredential(@Valid @RequestBody CredentialRequest r) {
        return credentials.create(r.membershipId(), r.name(), r.scopes() == null ? null : r.scopes().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()), r.expiresAt());
    }

    @Operation(
            summary = "List API credentials",
            description = "Retrieves all API credentials associated with the specified membership ID query parameter."
    )
    @GetMapping("/api-credentials")
    List<ApiCredentialService.CredentialView> credentials(@RequestParam UUID membershipId) {
        return credentials.list(membershipId);
    }

    @Operation(
            summary = "Revoke API credential",
            description = "Revokes an API credential by ID. Requires expectedVersion as a query parameter."
    )
    @DeleteMapping("/api-credentials/{id}")
    void revokeCredential(@PathVariable UUID id, @RequestParam long expectedVersion) {
        credentials.revoke(id, expectedVersion);
    }

    @Operation(
            summary = "Create webhook subscription",
            description = "Registers a new webhook subscription. Requires callbackUrl and optional eventFilter in the request body."
    )
    @PostMapping("/webhooks")
    WebhookService.SubscriptionView createWebhook(@Valid @RequestBody WebhookRequest r) {
        return webhooks.create(r.callbackUrl(), r.eventFilter());
    }

    @Operation(
            summary = "List webhook subscriptions",
            description = "Retrieves all registered webhook subscriptions for the current tenant."
    )
    @GetMapping("/webhooks")
    List<WebhookService.SubscriptionView> webhooks() {
        return webhooks.list();
    }

    @Operation(
            summary = "Revoke webhook subscription",
            description = "Deletes a webhook subscription by ID. Requires expectedVersion as a query parameter."
    )
    @DeleteMapping("/webhooks/{id}")
    void revokeWebhook(@PathVariable UUID id, @RequestParam long expectedVersion) {
        webhooks.revoke(id, expectedVersion);
    }

    @Operation(
            summary = "Configure storage integration",
            description = "Configures or updates tenant storage provider settings. Requires providerType, optional objectPrefix, and expectedVersion in the request body."
    )
    @PutMapping("/storage")
    StorageIntegrationService.BindingView configureStorage(@Valid @RequestBody StorageRequest r) {
        return storage.configure(r.providerType(), r.objectPrefix(), r.expectedVersion());
    }

    @Operation(
            summary = "Get current storage configuration",
            description = "Retrieves the current active storage provider integration settings."
    )
    @GetMapping("/storage")
    StorageIntegrationService.BindingView storage() {
        return storage.current();
    }

    @Operation(
            summary = "Configure identity provider",
            description = "Configures an OpenID Connect identity provider for single sign-on. Requires providerKey, issuer, authorizationEndpoint, clientId, redirectUri, and expectedVersion in the request body."
    )
    @PutMapping("/identity-providers")
    FederationService.ProviderView configureIdp(@Valid @RequestBody IdpRequest r) {
        return federation.configure(r.providerKey(), r.issuer(), r.authorizationEndpoint(), r.clientId(),
                r.clientSecret(), r.redirectUri(), r.scopes(), r.expectedVersion());
    }

    @Operation(
            summary = "List identity providers",
            description = "Retrieves all configured federation identity providers for the tenant."
    )
    @GetMapping("/identity-providers")
    List<FederationService.ProviderView> identityProviders() {
        return federation.list();
    }

    @Operation(
            summary = "Generate IdP authorization URL",
            description = "Generates the authorization redirect URL for an identity provider identified by key, using state and nonce query parameters."
    )
    @GetMapping("/identity-providers/{key}/authorize-url")
    String authorizeUrl(@PathVariable String key, @RequestParam String state, @RequestParam String nonce) {
        return federation.authorizationUrl(key, state, nonce);
    }

    @Operation(
            summary = "Synchronize federated identity",
            description = "Synchronizes identity attributes, group memberships, and role mappings from an identity provider identified by key. Requires subject and displayName in the request body."
    )
    @PostMapping("/identity-providers/{key}/synchronize")
    FederationIdentityDirectory.SyncResult synchronizeFederation(@PathVariable String key,
            @Valid @RequestBody FederationSyncRequest r) {
        return federation.synchronize(key, r.subject(), r.email(), r.displayName(), r.primaryBranchId(),
                r.externalReference(), r.groups(), r.roleMappings(), r.active());
    }

    @Operation(
            summary = "Configure external notification provider",
            description = "Configures an external notification provider. Requires providerKey, providerType, endpoint, optional credential, and expectedVersion in the request body."
    )
    @PutMapping("/notification-providers")
    ExternalNotificationService.ProviderView configureNotification(@Valid @RequestBody NotificationProviderRequest r) {
        return notifications.configure(r.providerKey(), r.providerType(), r.endpoint(), r.credential(),
                r.expectedVersion());
    }

    @Operation(
            summary = "List notification providers",
            description = "Retrieves all configured external notification providers."
    )
    @GetMapping("/notification-providers")
    List<ExternalNotificationService.ProviderView> notificationProviders() {
        return notifications.list();
    }

    @Operation(
            summary = "Test external notification provider",
            description = "Sends a test notification via the provider identified by key. Requires title and body map in the request body."
    )
    @PostMapping("/notification-providers/{key}/test")
    void testNotification(@PathVariable String key, @RequestBody Map<String, String> body) {
        notifications.send(key, body.get("title"), body.get("body"));
    }

    @Operation(
            summary = "Test webhook delivery",
            description = "Triggers a test webhook dispatch. Requires eventType and optional message in the request body."
    )
    @PostMapping("/webhooks/test")
    void testWebhook(@Valid @RequestBody TestWebhookRequest r) {
        webhooks.publish(r.eventType(),
                Map.of("test", true, "message", r.message() == null ? "Integration test" : r.message()));
    }

    record CredentialRequest(@NotNull UUID membershipId, @NotBlank @Size(max = 120) String name,
            @NotEmpty Set<PermissionKey> scopes, Instant expiresAt) {
    }

    record WebhookRequest(@NotBlank @Size(max = 1000) String callbackUrl, @Size(max = 64) String eventFilter) {
    }

    record TestWebhookRequest(@NotBlank @Size(max = 64) String eventType, @Size(max = 500) String message) {
    }

    record StorageRequest(@NotNull StorageProviderType providerType, @Size(max = 200) String objectPrefix,
            long expectedVersion) {
    }

    record IdpRequest(@NotBlank @Size(max = 64) String providerKey, @NotBlank String issuer,
            @NotBlank String authorizationEndpoint, @NotBlank String clientId, String clientSecret,
            @NotBlank String redirectUri, String scopes, long expectedVersion) {
    }

    record FederationSyncRequest(@NotBlank @Size(max = 160) String subject, @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName, UUID primaryBranchId,
            @Size(max = 160) String externalReference, Set<@Size(max = 160) String> groups,
            Map<@Size(max = 160) String, @NotBlank @Size(max = 64) String> roleMappings, boolean active) {
    }

    record NotificationProviderRequest(@NotBlank @Size(max = 64) String providerKey,
            @NotBlank @Size(max = 24) String providerType, @NotBlank @Size(max = 1000) String endpoint,
            String credential, long expectedVersion) {
    }
}
