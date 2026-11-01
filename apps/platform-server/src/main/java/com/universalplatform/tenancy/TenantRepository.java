package com.universalplatform.tenancy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);

    @Query("SELECT t FROM Tenant t WHERE t.deploymentPlatform = :platform AND t.deploymentStatus = :status")
    List<Tenant> findByDeploymentStatus(@Param("platform") String platform, @Param("status") String status);

    @Query("SELECT t FROM Tenant t WHERE t.subscriptionTier = :tier")
    List<Tenant> findBySubscriptionTier(@Param("tier") String tier);

    @Query("SELECT t FROM Tenant t WHERE t.licensedUserCount >= t.maxLicensedUsers")
    List<Tenant> findAtLicenseLimit();

    @Query("SELECT t FROM Tenant t WHERE t.lastActivityAt < :date")
    List<Tenant> findInactiveSince(@Param("date") Instant date);

    @Query("UPDATE Tenant t SET t.licensedUserCount = :count, t.lastActivityAt = :now WHERE t.id = :id")
    void updateLicensedUserCount(@Param("id") UUID id, @Param("count") int count, @Param("now") Instant now);

    @Query("UPDATE Tenant t SET t.deploymentStatus = :status, t.lastActivityAt = :now WHERE t.id = :id")
    void updateDeploymentStatus(@Param("id") UUID id, @Param("status") String status, @Param("now") Instant now);
}
