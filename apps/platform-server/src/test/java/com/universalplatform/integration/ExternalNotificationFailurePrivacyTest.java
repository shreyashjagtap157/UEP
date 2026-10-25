package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalNotificationFailurePrivacyTest {
    private final UUID tenantId = UUID.randomUUID();
    private final String endpoint = "https://hooks.example.com/incoming?token=do-not-log-this";

    @Mock TenantContext tenants;
    @Mock ActorContext actor;
    @Mock AuthorizationService auth;
    @Mock ExternalNotificationProviderRepository providers;
    @Mock IntegrationSecretCipher cipher;
    @Mock OutboundEndpointValidator endpointValidator;

    private ExternalNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ExternalNotificationService(tenants, actor, auth, providers, cipher, endpointValidator);
        when(tenants.requireTenantId()).thenReturn(tenantId);
        ExternalNotificationProvider provider = new ExternalNotificationProvider(
                UUID.randomUUID(), tenantId, "webhook", "HTTP_JSON", endpoint, null, Instant.now());
        when(providers.findByTenantIdAndProviderKey(tenantId, "webhook")).thenReturn(Optional.of(provider));
        when(endpointValidator.validate(endpoint)).thenThrow(new IllegalArgumentException(
                "Outbound endpoint must resolve only to public unicast addresses"));
    }

    @Test
    void deliveryFailureDoesNotExposeRawCallbackUrlOrNestedTransportException() {
        assertThatThrownBy(() -> service.send("webhook", "Test", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("External notification delivery failed")
                .satisfies(error -> {
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain("hooks.example.com", "do-not-log-this");
                });
    }
}
