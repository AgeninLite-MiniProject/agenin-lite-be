package com.indivaragroup.ageninlite.repository.invitation;

import com.indivaragroup.ageninlite.dto.dashboard.PendingInvitationDto;
import com.indivaragroup.ageninlite.entity.TrxInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrxInvitationRepository extends JpaRepository<TrxInvitation, UUID> {
    Optional<TrxInvitation> findByInviterIdAndInviteeId(UUID inviterId, UUID inviteeId);

    long countByInviterIdAndInvitationStatus(UUID inviterId, String invitationStatus);

    long countByInviteeIdAndInvitationStatus(UUID inviteeId, String invitationStatus);

    List<TrxInvitation> findAllByInviteeIdAndInvitationStatus(UUID inviteeId, String invitationStatus);

    Page<TrxInvitation> findByInviterIdAndInvitationStatus(UUID inviterId, String invitationStatus, Pageable pageable);

    Page<TrxInvitation> findByInviteeIdAndInvitationStatus(UUID inviteeId, String invitationStatus, Pageable pageable);

    @Query("""
    SELECT new com.indivaragroup.ageninlite.dto.dashboard.PendingInvitationDto(
        i.inviterId,
        u.userName,
        i.createdAt
    )
    FROM TrxInvitation i, MstUser u
    WHERE i.inviterId = u.userId
    AND i.inviteeId = :userId
    AND i.invitationStatus = 'PENDING'
    ORDER BY i.createdAt DESC
    """)
    List<PendingInvitationDto> findPendingInvitationsReceivedByUserId(@Param("userId") UUID userId);
}
