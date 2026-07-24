package com.indivaragroup.ageninlite.service.invitation;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.InvitationErrorCode;
import com.indivaragroup.ageninlite.common.utils.PhoneUtils;
import com.indivaragroup.ageninlite.common.constants.PaginationConstants;
import com.indivaragroup.ageninlite.common.enums.AuditOutcome;
import com.indivaragroup.ageninlite.common.enums.InvitationStatus;
import com.indivaragroup.ageninlite.dto.invitation.AcceptInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.CancelInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.DeclineInvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.InvitationResponse;
import com.indivaragroup.ageninlite.dto.invitation.SendInvitationRequest;
import com.indivaragroup.ageninlite.dto.invitation.SentInvitationItemDto;
import com.indivaragroup.ageninlite.dto.invitation.ReceivedInvitationItemDto;
import com.indivaragroup.ageninlite.dto.invitation.ReceivedInvitationListResponse;
import com.indivaragroup.ageninlite.dto.invitation.SentInvitationListResponse;
import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.EntityType;
import com.indivaragroup.ageninlite.service.audit.AuditService;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.entity.TrxInvitation;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.invitation.TrxInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {
    private static final int MAX_PENDING_INVITES = 3;
    private static final int MAX_DOWNLINERS_PER_USER = 10;

    private final TrxInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public InvitationResponse sendInvitation(UUID inviterId, SendInvitationRequest request) {
        String e164 = normalizeToE164(request.getPhoneNumber());

        MstUser invitee = userRepository.findByPhoneNumber(e164)
                .orElseThrow(() -> new AppException(InvitationErrorCode.INV_0023));

        if (invitee.isDeleted()) {
            throw new AppException(InvitationErrorCode.INV_0007);
        }

        UUID inviteeId = invitee.getUserId();

        if (inviterId.equals(inviteeId)) {
            throw new AppException(InvitationErrorCode.INV_0001);
        }

        if (invitee.getReferredBy() != null) {
            throw new AppException(InvitationErrorCode.INV_0004);
        }

        long pendingCount = invitationRepository.countByInviterIdAndInvitationStatus(inviterId, InvitationStatus.PENDING.name());
        if (pendingCount >= MAX_PENDING_INVITES) {
            throw new AppException(InvitationErrorCode.INV_0005);
        }

        Optional<TrxInvitation> existing = invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId);
        if (existing.isPresent()) {
            TrxInvitation inv = existing.get();
            if (InvitationStatus.PENDING.name().equals(inv.getInvitationStatus())) {
                throw new AppException(InvitationErrorCode.INV_0003);
            }
            log.info("invitation resent inviterId={} inviteeId={} previousStatus={}", inviterId, inviteeId, inv.getInvitationStatus());
            inv.setInvitationStatus(InvitationStatus.PENDING.name());
            inv.setRespondedAt(null);
            inv.setCancelledAt(null);
            TrxInvitation saved = invitationRepository.save(inv);
            auditService.saveLog(inviterId, AuditAction.INVITE_SENT, EntityType.INVITATION, saved.getInvitationId(), "Invitation resent to " + inviteeId, AuditOutcome.SUCCESS.name(), null, null);

            return buildInvitationResponse(inviterId, inviteeId, invitee, saved);
        }

        TrxInvitation invitation = TrxInvitation.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .invitationStatus(InvitationStatus.PENDING.name())
                .build();
        TrxInvitation saved = invitationRepository.save(invitation);
        log.info("invitation sent inviterId={} inviteeId={} invitationId={}", inviterId, inviteeId, saved.getInvitationId());

        auditService.saveLog(inviterId, AuditAction.INVITE_SENT, EntityType.INVITATION, saved.getInvitationId(), "Invitation sent to " + inviteeId, AuditOutcome.SUCCESS.name(), null, null);

        return buildInvitationResponse(inviterId, inviteeId, invitee, saved);
    }

    @Transactional
    public AcceptInvitationResponse acceptInvitation(UUID inviterId, UUID inviteeId) {
        TrxInvitation invitation = findPendingOrThrow(inviterId, inviteeId, InvitationErrorCode.INV_0013);

        MstUser invitee = findActiveInviteeOrThrow(inviteeId);
        if (invitee.getReferredBy() != null) {
            throw new AppException(InvitationErrorCode.INV_0012);
        }

        long inviterDownlinerCount = userRepository.countByReferredBy(inviterId);
        if (inviterDownlinerCount >= MAX_DOWNLINERS_PER_USER) {
            invitation.setInvitationStatus(InvitationStatus.EXPIRED.name());
            invitationRepository.save(invitation);
            auditService.saveLog(
                    inviteeId,
                    AuditAction.INVITATION_EXPIRED,
                    EntityType.INVITATION,
                    invitation.getInvitationId(),
                    "Invitation auto-expired (inviter " + inviterId + " at downliner cap",
                    AuditOutcome.SUCCESS.name(),
                    null, null
            );
            throw new AppException(InvitationErrorCode.INV_0010);
        }

        invitee.setReferredBy(inviterId);
        userRepository.save(invitee);

        markTerminal(invitation, InvitationStatus.ACCEPTED.name());

        List<TrxInvitation> otherPending = invitationRepository
                .findAllByInviteeIdAndInvitationStatus(inviteeId, InvitationStatus.PENDING.name())
                .stream()
                .filter(inv -> !inv.getInviterId().equals(inviterId))
                .toList();

        for (TrxInvitation other : otherPending) {
            log.info("competing invitation auto-expired inviteeId={} expiredInviterId={} acceptedInviterId={}", inviteeId, other.getInviterId(), inviterId);
            other.setInvitationStatus(InvitationStatus.EXPIRED.name());
            invitationRepository.save(other);
            auditService.saveLog(
                    inviteeId,
                    AuditAction.INVITATION_EXPIRED,
                    EntityType.INVITATION,
                    other.getInvitationId(),
                    "Invitation auto-expired (competing acceptance by inviter " + inviterId + ")",
                    AuditOutcome.SUCCESS.name(),
                    null, null
            );
        }

        log.info("invitation accepted inviterId={} inviteeId={} downlinerCount={}", inviterId, inviteeId, userRepository.countByReferredBy(inviterId));
        auditService.saveLog(inviteeId, AuditAction.INVITE_ACCEPTED, EntityType.INVITATION, invitation.getInvitationId(), "Invitation accepted from " + inviterId, AuditOutcome.SUCCESS.name(), null, null);

        return AcceptInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status(InvitationStatus.ACCEPTED.name())
                .respondedAt(invitation.getRespondedAt())
                .referredBy(inviterId)
                .cancelledCount(otherPending.size())
                .message("Invitation accepted")
                .build();
    }

    @Transactional
    public DeclineInvitationResponse declineInvitation(UUID inviterId, UUID inviteeId) {
        TrxInvitation invitation = findPendingOrThrow(inviterId, inviteeId, InvitationErrorCode.INV_0014);

        markTerminal(invitation, InvitationStatus.DECLINED.name());
        auditService.saveLog(inviteeId, AuditAction.INVITE_DECLINED, EntityType.INVITATION, invitation.getInvitationId(), "Invitation declined from " + inviterId, AuditOutcome.SUCCESS.name(), null, null);

        log.info("invitation declined inviterId={} inviteeId={}", inviterId, inviteeId);
        return DeclineInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status(InvitationStatus.DECLINED.name())
                .respondedAt(invitation.getRespondedAt())
                .message("Invitation declined")
                .build();
    }

    @Transactional
    public CancelInvitationResponse cancelInvitation(UUID inviterId, UUID inviteeId) {
        TrxInvitation invitation = invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)
                .orElseThrow(() -> new AppException(InvitationErrorCode.INV_0014));

        if (!InvitationStatus.PENDING.name().equals(invitation.getInvitationStatus())) {
            throw new AppException(InvitationErrorCode.INV_0011);
        }

        markTerminal(invitation, InvitationStatus.CANCELLED.name());
        auditService.saveLog(inviterId, AuditAction.INVITE_CANCELLED, EntityType.INVITATION, invitation.getInvitationId(), "Invitation cancelled to " + inviteeId, AuditOutcome.SUCCESS.name(), null, null);

        log.info("invitation cancelled inviterId={} inviteeId={}", inviterId, inviteeId);
        return CancelInvitationResponse.builder()
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .status(InvitationStatus.CANCELLED.name())
                .cancelledAt(invitation.getCancelledAt())
                .message("Invitation cancelled")
                .build();
    }

    @Transactional(readOnly = true)
    public SentInvitationListResponse listSentInvitations(
            UUID inviterId, String status, int page, int size) {

        String effectiveStatus = (status == null || status.isBlank()) ? InvitationStatus.PENDING.name() : status;

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            throw new AppException(InvitationErrorCode.INV_0020);
        }
        if (size <= 0) {
            size = PaginationConstants.INVITATION_DEFAULT_PAGE_SIZE;
        }
        if (page < 0) {
            page = 0;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TrxInvitation> result = invitationRepository
                .findByInviterIdAndInvitationStatus(inviterId, effectiveStatus, pageable);

        List<UUID> inviteeIds = result.getContent().stream()
                .map(TrxInvitation::getInviteeId)
                .distinct()
                .toList();
        Map<UUID, MstUser> inviteesById = byId(
                userRepository.findAllById(inviteeIds), MstUser::getUserId);

        List<SentInvitationItemDto> items = result.getContent().stream()
                .map(inv -> {
                    MstUser invitee = inviteesById.get(inv.getInviteeId());
                    return SentInvitationItemDto.builder()
                            .inviterId(inv.getInviterId())
                            .inviteeId(inv.getInviteeId())
                            .inviteeName(invitee != null ? invitee.getUserName() : null)
                            .inviteePhone(invitee != null ? invitee.getPhoneNumber() : null)
                            .status(inv.getInvitationStatus())
                            .createdAt(inv.getCreatedAt())
                            .respondedAt(inv.getRespondedAt())
                            .build();
                })
                .toList();

        long pendingCount = invitationRepository
                .countByInviterIdAndInvitationStatus(inviterId, InvitationStatus.PENDING.name());

        return SentInvitationListResponse.builder()
                .invitations(items)
                .pendingCount(pendingCount)
                .pendingCap(MAX_PENDING_INVITES)
                .build();
    }

    @Transactional(readOnly = true)
    public ReceivedInvitationListResponse listReceivedInvitations(
            UUID inviteeId, String status, int page, int size) {

        String effectiveStatus = (status == null || status.isBlank()) ? InvitationStatus.PENDING.name() : status;

        if (size > PaginationConstants.MAX_PAGE_SIZE) {
            throw new AppException(InvitationErrorCode.INV_0020);
        }
        if (size <= 0) {
            size = PaginationConstants.INVITATION_DEFAULT_PAGE_SIZE;
        }
        if (page < 0) {
            page = 0;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TrxInvitation> result = invitationRepository
                .findByInviteeIdAndInvitationStatus(inviteeId, effectiveStatus, pageable);

        List<UUID> inviterIds = result.getContent().stream()
                .map(TrxInvitation::getInviterId)
                .distinct()
                .toList();
        Map<UUID, MstUser> invitersById = byId(
                userRepository.findAllById(inviterIds), MstUser::getUserId);

        List<ReceivedInvitationItemDto> items = result.getContent().stream()
                .map(inv -> {
                    MstUser inviter = invitersById.get(inv.getInviterId());
                    return ReceivedInvitationItemDto.builder()
                            .inviterId(inv.getInviterId())
                            .inviterName(inviter != null ? inviter.getUserName() : null)
                            .inviterPhone(inviter != null ? inviter.getPhoneNumber() : null)
                            .inviterAvatarUrl(null)
                            .status(inv.getInvitationStatus())
                            .createdAt(inv.getCreatedAt())
                            .build();
                })
                .toList();

        long pendingCount = invitationRepository
                .countByInviteeIdAndInvitationStatus(inviteeId, InvitationStatus.PENDING.name());

        return ReceivedInvitationListResponse.builder()
                .invitations(items)
                .pendingCount(pendingCount)
                .build();
    }

    private TrxInvitation findPendingOrThrow(UUID inviterId, UUID inviteeId, InvitationErrorCode notFoundCode) {
        TrxInvitation invitation = invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)
                .orElseThrow(() -> new AppException(notFoundCode));
        if (!InvitationStatus.PENDING.name().equals(invitation.getInvitationStatus())) {
            throw new AppException(InvitationErrorCode.INV_0011);
        }
        return invitation;
    }

    private TrxInvitation markTerminal(TrxInvitation invitation, String newStatus) {
        invitation.setInvitationStatus(newStatus);
        if (InvitationStatus.CANCELLED.name().equals(newStatus)) {
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

    private static <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> keyFn) {
        return rows.stream().collect(Collectors.toMap(keyFn, x -> x));
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

    private String normalizeToE164(String raw) {
        try{
            return PhoneUtils.normalizeToE164(raw);
        }catch(IllegalArgumentException ex){
            throw new AppException(InvitationErrorCode.INV_0022);
        }
    }
}
