package com.indivaragroup.ageninlite.repository.auth;

import com.indivaragroup.ageninlite.entity.MstUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<MstUser, UUID> {
    Optional<MstUser> findByPhoneNumber(String phoneNumber);
    Optional<MstUser> findByReferralCode(String referralCode);
    boolean existsByPhoneNumber(String phoneNumber);
}
