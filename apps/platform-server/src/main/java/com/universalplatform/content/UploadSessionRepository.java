package com.universalplatform.content;
import java.time.Instant; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
interface UploadSessionRepository extends JpaRepository<UploadSession,UUID>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select u from UploadSession u where u.tenantId=:tenantId and u.id=:id") Optional<UploadSession> lock(@Param("tenantId") UUID tenantId,@Param("id") UUID id);
 List<UploadSession> findTop100ByStatusInAndStagingCleanedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc(
         Collection<UploadStatus> status, Instant before);
 List<UploadSession> findTop100ByStatusAndStagingCleanedAtIsNullAndCompletedAtBeforeOrderByCompletedAtAsc(
         UploadStatus status, Instant before);
}
