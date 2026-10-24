package com.universalplatform.integration;

import com.universalplatform.identity.FederationIdentityDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ApiCredentialService;
import com.universalplatform.storage.StorageProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/api-credentials")
    ApiCredentialService.CredentialView createCredential(@Valid @RequestBody CredentialRequest r) {
        return credentials.create(r.membershipId(), r.name(), r.scopes(), r.expiresAt());
    }

    @GetMapping("/api-credentials")
    List<ApiCredentialService.CredentialView> credentials(@RequestParam UUID membershipId) {
        return credentials.list(membershipId);
    }

    @DeleteMapping("/api-credentials/{id}")
    void revokeCredential(@PathVariable UUID id, @RequestParam long expectedVersion) {
        credentials.revoke(id, expectedVersion);
    }

    @PostMapping("/webhooks")
    WebhookService.SubscriptionView createWebhook(@Valid @RequestBody WebhookRequest r) {
        return webhooks.create(r.callbackUrl(), r.eventFilter());
    }

    @GetMapping("/webhooks")
    List<WebhookService.SubscriptionView> webhooks() {
        return webhooks.list();
    }

    @DeleteMapping("/webhooks/{id}")
    void revokeWebhook(@PathVariable UUID id, @RequestParam long expectedVersion) {
        webhooks.revoke(id, expectedVersion);
    }

    @PutMapping("/storage")
    StorageIntegrationService.BindingView configureStorage(@Valid @RequestBody StorageRequest r) {
        return storage.configure(r.providerType(), r.objectPrefix(), r.expectedVersion());
    }

    @GetMapping("/storage")
    StorageIntegrationService.BindingView storage() {
        return storage.current();
    }

    @PutMapping("/identity-providers")
    FederationService.ProviderView configureIdp(@Valid @RequestBody IdpRequest r) {
        return federation.configure(r.providerKey(), r.issuer(), r.authorizationEndpoint(), r.clientId(),
                r.clientSecret(), r.redirectUri(), r.scopes(), r.expectedVersion());
    }

    @GetMapping("/identity-providers")
    List<FederationService.ProviderView> identityProviders() {
        return federation.list();
    }

    @GetMapping("/identity-providers/{key}/authorize-url")
    String authorizeUrl(@PathVariable String key, @RequestParam String state, @RequestParam String nonce) {
        return federation.authorizationUrl(key, state, nonce);
    }

    @PostMapping("/identity-providers/{key}/synchronize")
    FederationIdentityDirectory.SyncResult synchronizeFederation(@PathVariable String key,
            @Valid @RequestBody FederationSyncRequest r) {
        return federation.synchronize(key, r.subject(), r.email(), r.displayName(), r.primaryBranchId(),
                r.externalReference(), r.groups(), r.roleMappings(), r.active());
    }

    @PutMapping("/notification-providers")
    ExternalNotificationService.ProviderView configureNotification(@Valid @RequestBody NotificationProviderRequest r) {
        return notifications.configure(r.providerKey(), r.providerType(), r.endpoint(), r.credential(),
                r.expectedVersion());
    }

    @GetMapping("/notification-providers")
    List<ExternalNotificationService.ProviderView> notificationProviders() {
        return notifications.list();
    }

    @PostMapping("/notification-providers/{key}/test")
    void testNotification(@PathVariable String key, @RequestBody Map<String, String> body) {
        notifications.send(key, body.get("title"), body.get("body"));
    }

    @PostMapping("/webhooks/test")
    void testWebhook(@Valid @RequestBody TestWebhookRequest r) {
        webhooks.publish(r.eventType(),
                Map.of("test", true, "message", r.message() == null ? "Integration test" : r.message()));
    }

    record CredentialRequest(@NotNull UUID membershipId, @NotBlank @Size(max = 120) String name,
            Set<PermissionKey> scopes, Instant expiresAt) {
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
