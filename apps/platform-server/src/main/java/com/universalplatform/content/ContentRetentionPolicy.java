package com.universalplatform.content;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="content_retention_policy") class ContentRetentionPolicy { @Id UUID tenantId; Integer defaultRetentionDays; @Column(nullable=false) int deletedObjectGraceDays; @Column(nullable=false) boolean retainVersions; @Column(nullable=false) Instant updatedAt; @Version @Column(name="row_version") long version; protected ContentRetentionPolicy(){} ContentRetentionPolicy(UUID t,Instant now){tenantId=t;deletedObjectGraceDays=30;retainVersions=true;updatedAt=now;} }
