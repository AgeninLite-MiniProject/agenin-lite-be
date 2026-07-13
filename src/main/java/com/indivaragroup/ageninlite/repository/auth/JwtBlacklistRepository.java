package com.indivaragroup.ageninlite.repository.auth;

import com.indivaragroup.ageninlite.entity.AuthJwtBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JwtBlacklistRepository extends JpaRepository<AuthJwtBlacklist, UUID> {
    boolean existsByTokenJti(UUID tokenJti);
}
