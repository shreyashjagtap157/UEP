package com.universalplatform.liveclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.universalplatform.audit.AuditService;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.scheduling.ScheduleDirectory;
import com.universalplatform.security.ActorContext;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.security.TenantContext;
import com.universalplatform.identity.PermissionKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class LiveClassServiceFeatureGateTest {
    private final TenantContext tenant = mock(TenantContext.class);
    private final ActorContext actor = mock(ActorContext.class);
    private final AuthorizationService auth = mock(AuthorizationService.class);
    private final MembershipDirectory memberships = mock(MembershipDirectory.class);
    private final EnrollmentDirectory enrollment = mock(EnrollmentDirectory.class);
    private final ScheduleDirectory schedules = mock(ScheduleDirectory.class);
    private final LiveClassRepository liveClasses = mock(LiveClassRepository.class);
    private final LiveClassParticipantRepository participants = mock(LiveClassParticipantRepository.class);
    private final LiveMediaProvider mediaProvider = mock(LiveMediaProvider.class);
    private final AuditService audit = mock(AuditService.class);

    @Test
    void cannotStartLiveClassWhenMediaProviderIsDisabled() {
        LiveClassService service = serviceWithMediaDisabled();

        assertThatThrownBy(() -> service.start(UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verifyNoInteractions(liveClasses, participants, schedules, audit);
    }

    @Test
    void cannotJoinLiveClassWhenMediaProviderIsDisabled() {
        LiveClassService service = serviceWithMediaDisabled();

        assertThatThrownBy(() -> service.join(UUID.randomUUID(), "BALANCED"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verifyNoInteractions(tenant, memberships, enrollment, liveClasses, participants, mediaProvider, audit);
    }

    @Test
    void listingLiveClassesRemainsAvailableWhenMediaProviderIsDisabled() {
        UUID tenantId = UUID.randomUUID();
        when(tenant.requireTenantId()).thenReturn(tenantId);
        when(auth.has(PermissionKey.LIVE_CLASS_MANAGE)).thenReturn(true);
        when(liveClasses.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        var result = serviceWithMediaDisabled().list(0, 10);

        assertThat(result.items()).isEmpty();
    }

    private LiveClassService serviceWithMediaDisabled() {
        LiveClassService service = new LiveClassService(tenant, actor, auth, memberships, enrollment, schedules,
                liveClasses, participants, mediaProvider, audit);
        ReflectionTestUtils.setField(service, "liveKitEnabled", false);
        return service;
    }
}
