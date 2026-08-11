package com.universalplatform.content;
import java.time.Instant; import java.util.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
interface LearningResourceRepository extends JpaRepository<LearningResource,UUID>{
 Optional<LearningResource> findByTenantIdAndId(UUID tenantId,UUID id); List<LearningResource> findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant before); Page<LearningResource> findAllByTenantId(UUID tenantId,Pageable p);
 @Query("""
 select r from LearningResource r where r.tenantId=:tenantId
 and (r.releaseAt is null or r.releaseAt<=:now) and (r.expiresAt is null or r.expiresAt>:now)
 and (r.visibility=com.universalplatform.content.ResourceVisibility.TENANT
   or (r.visibility=com.universalplatform.content.ResourceVisibility.PRIVATE and r.createdBy=:subject)
   or (:staff=true and r.visibility=com.universalplatform.content.ResourceVisibility.STAFF)
   or (r.visibility=com.universalplatform.content.ResourceVisibility.ENROLLED_LEARNERS and r.courseId in :courseIds))""")
 Page<LearningResource> findVisible(@Param("tenantId")UUID tenantId,@Param("subject")String subject,@Param("staff")boolean staff,@Param("courseIds")Collection<UUID> courseIds,@Param("now")Instant now,Pageable p);
}
