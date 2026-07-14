package com.indivaragroup.ageninlite.repository.auth;

import com.indivaragroup.ageninlite.dto.dashboard.DownlinerDto;
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

    long countByRoleAndUserStatusAndIsDeletedFalse(String role, String userStatus);

    long countByRole(String role);

    @Query("""
            SELECT new com.indivaragroup.ageninlite.dto.dashboard.DownlinerDto(
                u.userId,
                u.userName,
                u.phoneNumber
            )
            FROM MstUser u
            WHERE u.referredBy = :userId
            """)
    List<DownlinerDto> findDirectDownlinersByUserId(@Param("userId") UUID userId);

    @Query("SELECT u FROM MstUser u WHERE u.role = 'AGENT' AND " +
            "(:query IS NULL OR :query = '' OR LOWER(u.userName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "(:status IS NULL OR u.userStatus = :status) AND " +
            "(:isDeleted IS NULL OR u.isDeleted = :isDeleted)")
    Page<MstUser> searchUsers(
            @Param("query") String query,
            @Param("status") String status,
            @Param("isDeleted") Boolean isDeleted,
            Pageable pageable);
    Page<MstUser> findByRole(String role, Pageable pageable);

    @Query("SELECT u FROM MstUser u WHERE " +
            "u.role = 'AGENT' AND " +
            "u.isDeleted = false AND " +
            "u.referredBy IS NULL AND " +
            "u.phoneNumber LIKE CONCAT(:prefix, '%') " +
            "ORDER BY u.phoneNumber ASC")
    Page<MstUser> searchAgentsByPhonePrefix(
            @Param("prefix") String prefix,
            Pageable pageable);

}
