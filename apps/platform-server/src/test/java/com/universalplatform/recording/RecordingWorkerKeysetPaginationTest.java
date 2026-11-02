package com.universalplatform.recording;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.security.TenantExecutionContext;
import com.universalplatform.storage.StorageProviderType;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RecordingWorkerKeysetPaginationTest {
    private static final int CANDIDATE_COUNT = 205;
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant READY_AT = Instant.parse("2026-10-02T00:00:00Z");

    @Mock RecordingRepository recordings;
    @Mock RecordingService service;
    @Mock TenantExecutionContext tenantExecution;

    private RecordingWorker worker;
    private List<Recording> candidates;
    private Map<UUID, Recording> byId;

    @BeforeEach
    void setUp() {
        worker = new RecordingWorker(recordings, service, tenantExecution);
        candidates = java.util.stream.IntStream.range(0, CANDIDATE_COUNT)
                .mapToObj(this::recording)
                .sorted(Comparator.comparing(Recording::id))
                .toList();
        byId = new HashMap<>();
        candidates.forEach(recording -> byId.put(recording.id(), recording));

        lenient().when(recordings.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(byId.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantExecution).run(any(UUID.class), any(Runnable.class));
    }

    @Test
    void processVisitsEveryCandidateAcrossMoreThanTwoPages() {
        candidates.forEach(recording -> recording.status = RecordingProcessingStatus.REQUESTED);
        when(recordings.findProcessableAfter(anyCollection(), any(Instant.class), any(UUID.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    List<RecordingProcessingStatus> statuses = invocation.getArgument(0);
                    return page(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3),
                            Recording::requestedAt, recording -> statuses.contains(recording.status), null);
                });

        worker.process();

        verify(service, times(CANDIDATE_COUNT)).processOrReconcile(any(Recording.class));
        verify(recordings, times(3)).findProcessableAfter(
                anyCollection(), any(Instant.class), any(UUID.class), any(Pageable.class));
    }

    @Test
    void archiveVisitsEveryCandidateAcrossMoreThanTwoPages() {
        when(recordings.findArchiveCandidatesAfter(
                anyCollection(), any(Instant.class), any(Instant.class), any(UUID.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    List<RecordingProcessingStatus> statuses = invocation.getArgument(0);
                    Instant before = invocation.getArgument(1);
                    return page(invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4),
                            Recording::readyAt, recording -> statuses.contains(recording.status())
                                    && recording.readyAt().isBefore(before), null);
                });

        worker.archive();

        verify(service, times(CANDIDATE_COUNT)).archiveDue(any(Recording.class));
        verify(recordings, times(3)).findArchiveCandidatesAfter(
                anyCollection(), any(Instant.class), any(Instant.class), any(UUID.class), any(Pageable.class));
    }

    @Test
    void purgeVisitsEveryCandidateAcrossMoreThanTwoPages() {
        when(recordings.findPurgeCandidatesAfter(
                anyCollection(), any(Instant.class), any(UUID.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    List<RecordingProcessingStatus> statuses = invocation.getArgument(0);
                    return page(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3),
                            Recording::readyAt, recording -> statuses.contains(recording.status()), null);
                });

        worker.purge();

        verify(service, times(CANDIDATE_COUNT)).purgeDue(any(Recording.class));
        verify(recordings, times(3)).findPurgeCandidatesAfter(
                anyCollection(), any(Instant.class), any(UUID.class), any(Pageable.class));
    }

    @Test
    void failedPurgeCandidateDoesNotBlockLaterRecordingsInTheSamePage() {
        Recording first = candidates.get(0);
        Recording second = candidates.get(1);
        Recording third = candidates.get(2);
        when(recordings.findPurgeCandidatesAfter(
                anyCollection(), any(Instant.class), any(UUID.class), any(Pageable.class)))
                .thenReturn(List.of(first, second, third));
        doThrow(new IllegalStateException("cleanup outbox unavailable"))
                .when(service).purgeDue(first);

        worker.purge();

        verify(service).purgeDue(first);
        verify(service).purgeDue(second);
        verify(service).purgeDue(third);
    }

    private Recording recording(int index) {
        Recording recording = new Recording(UUID.randomUUID(), TENANT_ID, UUID.randomUUID(), UUID.randomUUID(),
                "worker-test-room", RecordingQualityPreset.BALANCED, StorageProviderType.LOCAL,
                REQUESTED_AT.plusSeconds(index));
        recording.ready("locator-" + index, "key-" + index, "a".repeat(64), 100, 10, READY_AT);
        return recording;
    }

    private List<Recording> page(Instant cursorAt, UUID cursorId, Pageable pageable,
                                 Function<Recording, Instant> timestamp,
                                 Predicate<Recording> additionalFilter, Object unused) {
        return candidates.stream()
                .filter(additionalFilter)
                .filter(recording -> timestamp.apply(recording).isAfter(cursorAt)
                        || (timestamp.apply(recording).equals(cursorAt)
                        && recording.id().compareTo(cursorId) > 0))
                .sorted((left, right) -> {
                    int timeOrder = timestamp.apply(left).compareTo(timestamp.apply(right));
                    return timeOrder != 0 ? timeOrder : left.id().compareTo(right.id());
                })
                .limit(pageable.getPageSize())
                .toList();
    }
}
