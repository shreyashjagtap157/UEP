package com.universalplatform.organization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {}
