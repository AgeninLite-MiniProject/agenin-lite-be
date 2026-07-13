package com.indivaragroup.ageninlite.repository.invitation;

import com.indivaragroup.ageninlite.entity.TrxInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
