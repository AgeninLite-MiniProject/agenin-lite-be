package com.indivaragroup.ageninlite.service.invitation;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.InvitationErrorCode;
import com.indivaragroup.ageninlite.dto.invitation.AcceptInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.CancelInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.DeclineInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.InvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.SendInvitationRequest;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.entity.TrxInvitation;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.invitation.TrxInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {
    private static final int MAX_PENDING_INVITES = 3;
    private static final int MAX_DOWNLINERS_PER_USER = 10;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_DECLINED = "DECLINED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final TrxInvitationRepository invitationRepository;
    private final UserRepository userRepository;

    @Transactional
    public InvitationResponse send(UUID inviterId, SendInvitationRequest request) {
        UUID inviteeId = request.getInviteeId();

        if (inviterId.equals(inviteeId)) {
            throw new AppException(InvitationErrorCode.INV_0001);
        }

        MstUser invitee = findActiveInviteeOrThrow(inviteeId);

        if (invitee.getReferredBy() != null) {
            throw new AppException(InvitationErrorCode.INV_0004);
        }

        long pendingCount = invitationRepository.countByInviterIdAndInvitationStatus(inviterId, STATUS_PENDING);
        if (pendingCount >= MAX_PENDING_INVITES) {
            throw new AppException(InvitationErrorCode.INV_0005);
        }

        Optional<TrxInvitation> existing = invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId);
        if (existing.isPresent()) {
            TrxInvitation inv = existing.get();
            if (STATUS_PENDING.equals(inv.getInvitationStatus())) {
                throw new AppException(InvitationErrorCode.INV_0003);
            }
            inv.setInvitationStatus(STATUS_PENDING);
            inv.setRespondedAt(null);
            inv.setCancelledAt(null);
            TrxInvitation saved = invitationRepository.save(inv);
            // TODO: audit — action="INVITE_RESENT", payload={ inviterId, inviteeId, previousStatus }

            return buildInvitationResponse(inviterId, inviteeId, invitee, saved);
        }

        TrxInvitation invitation = TrxInvitation.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .invitationStatus(STATUS_PENDING)
                .build();
        TrxInvitation saved = invitationRepository.save(invitation);

        // TODO: audit — call AuditService.log(actorId=inviterId, action="INVITE_SENT", entityType="INVITATION", entityId=saved.getInvitationId(), payload=...)
        // Will be implemented when AuditService exists.

        return buildInvitationResponse(inviterId, inviteeId, invitee, saved);
    }

    @Transactional
    public AcceptInvitationResponse accept(UUID inviterId, UUID inviteeId) {
        TrxInvitation invitation = findPendingOrThrow(inviterId, inviteeId, InvitationErrorCode.INV_0013);

        MstUser invitee = findActiveInviteeOrThrow(inviteeId);
        if (invitee.getReferredBy() != null) {
            throw new AppException(InvitationErrorCode.INV_0012);
        }

        long inviterDownlinerCount = userRepository.countByReferredBy(inviterId);
        if (inviterDownlinerCount >= MAX_DOWNLINERS_PER_USER) {
            invitation.setInvitationStatus(STATUS_EXPIRED);
            invitationRepository.save(invitation);
            throw new AppException(InvitationErrorCode.INV_0010);
        }

        invitee.setReferredBy(inviterId);
        userRepository.save(invitee);

        markTerminal(invitation, STATUS_ACCEPTED);

        List<TrxInvitation> otherPending = invitationRepository
                .findAllByInviteeIdAndInvitationStatus(inviteeId, STATUS_PENDING)
                .stream()
                .filter(inv -> !inv.getInviterId().equals(inviterId))
                .toList();

        for (TrxInvitation other : otherPending) {
            other.setInvitationStatus(STATUS_EXPIRED);
            invitationRepository.save(other);
        }

        return AcceptInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status(STATUS_ACCEPTED)
                .respondedAt(invitation.getRespondedAt())
                .referredBy(inviterId)
                .cancelledCount(otherPending.size())
                .message("Invitation accepted")
                .build();
    }

    @Transactional
    public DeclineInvitationResponse decline(UUID inviterId, UUID inviteeId) {
        TrxInvitation invitation = findPendingOrThrow(inviterId, inviteeId, InvitationErrorCode.INV_0014);

        markTerminal(invitation, STATUS_DECLINED);
        // TODO: audit INVITE_DECLINED with payload { inviterId, inviteeId }

        return DeclineInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status(STATUS_DECLINED)
                .respondedAt(invitation.getRespondedAt())
                .message("Invitation declined")
                .build();
    }

    @Transactional
    public CancelInvitationResponse cancel(UUID inviterId, UUID inviteeId) {
        TrxInvitation invitation = invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)
                .orElseThrow(() -> new AppException(InvitationErrorCode.INV_0014));

        if (!STATUS_PENDING.equals(invitation.getInvitationStatus())) {
            throw new AppException(InvitationErrorCode.INV_0011);
        }

        markTerminal(invitation, STATUS_CANCELLED);
        // TODO: audit INVITE_CANCELLED with payload { inviterId, inviteeId }

        return CancelInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status(STATUS_CANCELLED)
                .cancelledAt(invitation.getCancelledAt())
                .message("Invitation cancelled")
                .build();
    }

    private TrxInvitation findPendingOrThrow(UUID inviterId, UUID inviteeId, InvitationErrorCode notFoundCode) {
        TrxInvitation invitation = invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)
                .orElseThrow(() -> new AppException(notFoundCode));
        if (!STATUS_PENDING.equals(invitation.getInvitationStatus())) {
            throw new AppException(InvitationErrorCode.INV_0011);
        }
        return invitation;
    }

    private TrxInvitation markTerminal(TrxInvitation invitation, String newStatus) {
        invitation.setInvitationStatus(newStatus);
        if (STATUS_CANCELLED.equals(newStatus)) {
            invitation.setCancelledAt(LocalDateTime.now());
        } else {
            invitation.setRespondedAt(LocalDateTime.now());
        }
        return invitationRepository.save(invitation);
    }

    private MstUser findActiveInviteeOrThrow(UUID inviteeId) {
        MstUser invitee = userRepository.findById(inviteeId)
                .orElseThrow(() -> new AppException(InvitationErrorCode.INV_0006));
        if (invitee.isDeleted()) {
            throw new AppException(InvitationErrorCode.INV_0007);
        }
        return invitee;
    }

    private InvitationResponse buildInvitationResponse(UUID inviterId, UUID inviteeId, MstUser invitee, TrxInvitation saved) {
        return InvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .inviteeName(invitee.getUserName())
                .status(saved.getInvitationStatus())
                .createdAt(saved.getCreatedAt())
                .message("Invitation sent successfully")
                .build();
    }
}
