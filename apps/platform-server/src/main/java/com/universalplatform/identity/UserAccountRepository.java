package com.universalplatform.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByOidcSubject(String oidcSubject);
}
