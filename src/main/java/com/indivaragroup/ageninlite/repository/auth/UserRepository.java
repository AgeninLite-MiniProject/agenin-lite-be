package com.indivaragroup.ageninlite.repository.auth;

import com.indivaragroup.ageninlite.entity.MstUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<MstUser, UUID> {
    Optional<MstUser> findByPhoneNumber(String phoneNumber);
    Optional<MstUser> findByReferralCode(String referralCode);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    int countByReferredBy(UUID referredBy);

    @Query("SELECT u FROM MstUser u WHERE u.isDeleted = false AND u.role = 'AGENT' AND " +
           "(LOWER(u.userName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<MstUser> searchUsers(@Param("query") String query, Pageable pageable);

    Page<MstUser> findByIsDeletedFalseAndRole(String role, Pageable pageable);
}
