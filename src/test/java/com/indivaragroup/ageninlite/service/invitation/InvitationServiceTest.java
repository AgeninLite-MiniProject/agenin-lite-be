package com.indivaragroup.ageninlite.service.invitation;

import com.indivaragroup.ageninlite.common.enums.AuditAction;
import com.indivaragroup.ageninlite.common.enums.AuditOutcome;
import com.indivaragroup.ageninlite.common.enums.EntityType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private TrxInvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;
    @Mock
    private com.indivaragroup.ageninlite.service.audit.AuditService auditService;

    @InjectMocks
    private InvitationService invitationService;

    private UUID inviterId;
    private UUID inviteeId;
    private UUID otherInviterId;
    private MstUser inviter;
    private MstUser invitee;
    private SendInvitationRequest request;
    private SendInvitationRequest localFormatRequest;
    private TrxInvitation otherPendingInvitation;

    @BeforeEach
    void setUp() {
        inviterId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();
        otherInviterId = UUID.randomUUID();

        inviter = MstUser.builder()
                .userId(inviterId)
                .userName("Inviter Name")
                .phoneNumber("+628111111111")
                .passwordHash("hash")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .build();

        invitee = MstUser.builder()
                .userId(inviteeId)
                .userName("Invitee Name")
                .phoneNumber("+628222222222")
                .passwordHash("hash")
                .role("AGENT")
                .userStatus("PASSIVE")
                .isDeleted(false)
                .referredBy(null)
                .build();

        request = new SendInvitationRequest("+628222222222");
        localFormatRequest = new SendInvitationRequest("0812-345-6789");

        otherPendingInvitation = TrxInvitation.builder()
                .invitationId(UUID.randomUUID())
                .inviterId(otherInviterId)
                .inviteeId(inviteeId)
                .invitationStatus("PENDING")
                .build();
    }

    private TrxInvitation buildInvitation(String status) {
        return TrxInvitation.builder()
                .invitationId(UUID.randomUUID())
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .invitationStatus(status)
                .respondedAt(null)
                .cancelledAt(null)
                .build();
    }

    // ==================== Group 1: send() ====================

    @Test
    void send_Invitation_WhenNewPair_ShouldPersistPendingInvitation() {
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.empty());
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        InvitationResponse result = invitationService.sendInvitation(inviterId, request);

        assertNotNull(result);
        assertEquals(inviterId, result.getInviterId());
        assertEquals(inviteeId, result.getInviteeId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("Invitee Name", result.getInviteeName());
        verify(invitationRepository).save(any(TrxInvitation.class));
    }

    @Test
    void send_Invitation_WhenExistingDeclined_ShouldResetTimestampsAndReinvite() {
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("DECLINED")));
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        InvitationResponse result = invitationService.sendInvitation(inviterId, request);

        assertEquals("PENDING", result.getStatus());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository).save(captor.capture());
        TrxInvitation saved = captor.getValue();
        assertNull(saved.getRespondedAt());
        assertNull(saved.getCancelledAt());
    }

    @Test
    void send_Invitation_WhenSelfInvite_ShouldThrowInv0001() {
        SendInvitationRequest selfRequest = new SendInvitationRequest(inviter.getPhoneNumber());
        when(userRepository.findByPhoneNumber(inviter.getPhoneNumber())).thenReturn(Optional.of(inviter));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, selfRequest));
        assertEquals(InvitationErrorCode.INV_0001, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviteeHasUpline_ShouldThrowInv0004() {
        invitee.setReferredBy(UUID.randomUUID());
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.of(invitee));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0004, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviterHas3Pending_ShouldThrowInv0005() {
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(3L);

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0005, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenDuplicatePending_ShouldThrowInv0003() {
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0003, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenPhoneNotRegistered_ShouldThrowInv0023() {
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0023, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviteeDeleted_ShouldThrowInv0007() {
        invitee.setDeleted(true);
        when(userRepository.findByPhoneNumber("+628222222222")).thenReturn(Optional.of(invitee));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0007, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    // ==================== Group 1b: send() by phone ====================

    @Test
    void send_Invitation_WhenLocalFormatPhone_ShouldNormalizeAndLookup() {
        when(userRepository.findByPhoneNumber("+628123456789"))
                .thenReturn(Optional.of(MstUser.builder()
                        .userId(UUID.randomUUID())
                        .userName("Local Format User")
                        .phoneNumber("+628123456789")
                        .passwordHash("h")
                        .role("AGENT")
                        .userStatus("PASSIVE")
                        .isDeleted(false)
                        .referredBy(null)
                        .build()));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(any(), any())).thenReturn(Optional.empty());
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        SendInvitationRequest localReq = new SendInvitationRequest("0812-345-6789");
        InvitationResponse result = invitationService.sendInvitation(inviterId, localReq);

        assertEquals("PENDING", result.getStatus());
        assertEquals("Local Format User", result.getInviteeName());
        verify(userRepository).findByPhoneNumber("+628123456789");
        verify(userRepository, never()).findByPhoneNumber("0812-345-6789");
    }

    @Test
    void send_Invitation_WhenCountryCodeWithoutPlus_ShouldNormalize() {
        when(userRepository.findByPhoneNumber("+628123456789")).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(any(), any())).thenReturn(Optional.empty());
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        SendInvitationRequest noPlusReq = new SendInvitationRequest("628123456789");
        InvitationResponse result = invitationService.sendInvitation(inviterId, noPlusReq);

        assertEquals("PENDING", result.getStatus());
        verify(userRepository).findByPhoneNumber("+628123456789");
    }

    @Test
    void send_Invitation_WhenPhoneWithFormattingNoise_ShouldNormalize() {
        when(userRepository.findByPhoneNumber("+628123456789")).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(any(), any())).thenReturn(Optional.empty());
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        SendInvitationRequest messyReq = new SendInvitationRequest("(0812) 345-6789");
        InvitationResponse result = invitationService.sendInvitation(inviterId, messyReq);

        assertEquals("PENDING", result.getStatus());
        verify(userRepository).findByPhoneNumber("+628123456789");
    }

    @Test
    void send_Invitation_WhenPhoneContainsLetters_ShouldThrowInv0022() {
        SendInvitationRequest garbage = new SendInvitationRequest("0812abc456");

        AppException ex = assertThrows(AppException.class,
                () -> invitationService.sendInvitation(inviterId, garbage));
        assertEquals(InvitationErrorCode.INV_0022, ex.getErrorCode());
        verify(userRepository, never()).findByPhoneNumber(any());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenPhoneIsBlank_ShouldThrowInv0022() {
        SendInvitationRequest blank = new SendInvitationRequest("   ");

        AppException ex = assertThrows(AppException.class,
                () -> invitationService.sendInvitation(inviterId, blank));
        assertEquals(InvitationErrorCode.INV_0022, ex.getErrorCode());
        verify(userRepository, never()).findByPhoneNumber(any());
    }

    @Test
    void send_Invitation_WhenPhoneHasNoValidPrefix_ShouldThrowInv0022() {
        SendInvitationRequest weird = new SendInvitationRequest("12345678");

        AppException ex = assertThrows(AppException.class,
                () -> invitationService.sendInvitation(inviterId, weird));
        assertEquals(InvitationErrorCode.INV_0022, ex.getErrorCode());
    }

    @Test
    void send_Invitation_WhenPhoneTooShort_ShouldThrowInv0022() {
        SendInvitationRequest tooShort = new SendInvitationRequest("08123");

        AppException ex = assertThrows(AppException.class,
                () -> invitationService.sendInvitation(inviterId, tooShort));
        assertEquals(InvitationErrorCode.INV_0022, ex.getErrorCode());
    }

    @Test
    void send_Invitation_WhenPhoneIsNull_ShouldThrowInv0022() {
        SendInvitationRequest nullPhone = new SendInvitationRequest(null);

        AppException ex = assertThrows(AppException.class,
                () -> invitationService.sendInvitation(inviterId, nullPhone));
        assertEquals(InvitationErrorCode.INV_0022, ex.getErrorCode());
        verify(userRepository, never()).findByPhoneNumber(any());
    }

    // ==================== Group 2: accept() ====================

    @Test
    void accept_WhenCleanAndNoCompetitors_ShouldAcceptInvitationAndRefer() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(userRepository.countByReferredBy(inviterId)).thenReturn(5);
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);
        when(invitationRepository.findAllByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(List.of());

        AcceptInvitationResponse result = invitationService.acceptInvitation(inviterId, inviteeId);

        assertEquals("ACCEPTED", result.getStatus());
        assertEquals(0, result.getCancelledCount());
        assertEquals(inviterId, result.getReferredBy());
        assertNotNull(result.getRespondedAt());
        assertEquals(inviterId, invitee.getReferredBy());
        verify(auditService, never()).saveLog(
                any(), eq(AuditAction.INVITATION_EXPIRED), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void accept_Invitation_WhenOneCompetingPending_ShouldExpireCompetitor() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(userRepository.countByReferredBy(inviterId)).thenReturn(5);
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);
        when(invitationRepository.findAllByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(List.of(otherPendingInvitation));

        AcceptInvitationResponse result = invitationService.acceptInvitation(inviterId, inviteeId);

        assertEquals(1, result.getCancelledCount());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository, times(2)).save(captor.capture());
        List<TrxInvitation> savedInvs = captor.getAllValues();
        assertEquals("EXPIRED", savedInvs.get(1).getInvitationStatus());

        verify(auditService, times(1)).saveLog(
                eq(inviteeId),
                eq(AuditAction.INVITATION_EXPIRED),
                eq(EntityType.INVITATION),
                eq(otherPendingInvitation.getInvitationId()),
                argThat(msg -> msg.contains("competing acceptance") && msg.contains(inviterId.toString())),
                eq(AuditOutcome.SUCCESS.name()),
                isNull(),
                isNull()
        );

// The winning invitation was accepted → INVITE_ACCEPTED audit row was also written (regression guard)
        verify(auditService, times(1)).saveLog(
                eq(inviteeId),
                eq(AuditAction.INVITE_ACCEPTED),
                eq(EntityType.INVITATION),
                any(),
                anyString(),
                eq(AuditOutcome.SUCCESS.name()),
                isNull(),
                isNull()
        );
    }

    @Test
    void accept_Invitation_WhenInvitationNotFound_ShouldThrowInv0013() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invitationService.acceptInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0013, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void accept_Invitation_WhenStatusNotPending_ShouldThrowInv0011() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("ACCEPTED")));

        AppException ex = assertThrows(AppException.class, () -> invitationService.acceptInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0011, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void accept_Invitation_WhenInviteeNowHasUpline_ShouldThrowInv0012() {
        invitee.setReferredBy(UUID.randomUUID());
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));

        AppException ex = assertThrows(AppException.class, () -> invitationService.acceptInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0012, ex.getErrorCode());
        verify(userRepository, never()).save(any(MstUser.class));
        verify(invitationRepository, never()).save(any(TrxInvitation.class));
    }

    @Test
    void accept_Invitation_WhenInviteeNotFound_ShouldThrowInv0006() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invitationService.acceptInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0006, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void accept_Invitation_WhenInviteeDeleted_ShouldThrowInv0007() {
        invitee.setDeleted(true);
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));

        AppException ex = assertThrows(AppException.class, () -> invitationService.acceptInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0007, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void accept_Invitation_WhenPendingListIncludesOwnInvitation_ShouldFilterItOut() {
        TrxInvitation ownPending = TrxInvitation.builder()
                .invitationId(UUID.randomUUID())
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .invitationStatus("PENDING")
                .build();

        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(userRepository.countByReferredBy(inviterId)).thenReturn(5);
        when(userRepository.save(any(MstUser.class))).thenAnswer(i -> i.getArguments()[0]);
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);
        when(invitationRepository.findAllByInviteeIdAndInvitationStatus(inviteeId, "PENDING"))
                .thenReturn(List.of(ownPending, otherPendingInvitation));

        AcceptInvitationResponse result = invitationService.acceptInvitation(inviterId, inviteeId);

        assertEquals(1, result.getCancelledCount());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository, times(2)).save(captor.capture());
        assertEquals("EXPIRED", captor.getAllValues().get(1).getInvitationStatus());
    }

    @Test
    void accept_Invitation_WhenInviterAt10Downliners_ShouldExpireWithoutAccepting() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(userRepository.countByReferredBy(inviterId)).thenReturn(10);
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        AcceptInvitationResponse result = invitationService.acceptInvitation(inviterId, inviteeId);

        assertEquals("EXPIRED", result.getStatus());
        assertNull(result.getReferredBy());
        assertEquals(0, result.getCancelledCount());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository).save(captor.capture());
        assertEquals("EXPIRED", captor.getValue().getInvitationStatus());
        verify(userRepository, never()).save(any(MstUser.class));
        verify(auditService, times(1)).saveLog(
                eq(inviteeId),
                eq(AuditAction.INVITATION_EXPIRED),
                eq(EntityType.INVITATION),
                any(),
                argThat(msg -> msg.contains("downliner cap") && msg.contains(inviterId.toString())),
                eq(AuditOutcome.SUCCESS.name()),
                isNull(),
                isNull()
        );
    }
    // ==================== Group 3: decline() ====================

    @Test
    void decline_Invitation_WhenPending_ShouldSetDeclinedAndRespondedAt() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        DeclineInvitationResponse result = invitationService.declineInvitation(inviterId, inviteeId);

        assertEquals("DECLINED", result.getStatus());
        assertNotNull(result.getRespondedAt());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository).save(captor.capture());
        TrxInvitation saved = captor.getValue();
        assertEquals("DECLINED", saved.getInvitationStatus());
        assertNotNull(saved.getRespondedAt());
        assertNull(saved.getCancelledAt());
    }

    @Test
    void decline_Invitation_WhenInvitationNotFound_ShouldThrowInv0014() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invitationService.declineInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0014, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void decline_Invitation_WhenStatusNotPending_ShouldThrowInv0011() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("ACCEPTED")));

        AppException ex = assertThrows(AppException.class, () -> invitationService.declineInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0011, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    // ==================== Group 4: cancel() ====================

    @Test
    void cancel_Invitation_WhenPendingAndOwner_ShouldSetCancelledAndCancelledAt() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        CancelInvitationResponse result = invitationService.cancelInvitation(inviterId, inviteeId);

        assertEquals("CANCELLED", result.getStatus());
        assertNotNull(result.getCancelledAt());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository).save(captor.capture());
        TrxInvitation saved = captor.getValue();
        assertEquals("CANCELLED", saved.getInvitationStatus());
        assertNotNull(saved.getCancelledAt());
        assertNull(saved.getRespondedAt());
    }

    @Test
    void cancel_Invitation_WhenInvitationNotFound_ShouldThrowInv0014() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invitationService.cancelInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0014, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void cancel_Invitation_WhenStatusNotPending_ShouldThrowInv0011() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("ACCEPTED")));

        AppException ex = assertThrows(AppException.class, () -> invitationService.cancelInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0011, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    // ==================== Group 5: listSentInvitations() ====================

    @Test
    void listSent_WhenHappyPath_ShouldReturnItemsWithInviteeNames() {
        TrxInvitation sent = buildInvitation("PENDING");
        when(invitationRepository.findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any())).thenReturn(pageOf(sent));
        when(userRepository.findAllById(any())).thenReturn(List.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);

        var result = invitationService.listSentInvitations(inviterId, "PENDING", 0, 10);

        assertEquals(1, result.getInvitations().size());
        assertEquals("Invitee Name", result.getInvitations().get(0).getInviteeName());
        assertEquals("+628222222222", result.getInvitations().get(0).getInviteePhone());
        assertEquals(0L, result.getPendingCount());
        assertEquals(3, result.getPendingCap());
    }

    @Test
    void listSent_WhenInviteeMissingInBatch_ShouldReturnNullNameAndPhone() {
        TrxInvitation sent = buildInvitation("PENDING");
        when(invitationRepository.findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any())).thenReturn(pageOf(sent));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);

        var result = invitationService.listSentInvitations(inviterId, "PENDING", 0, 10);

        assertNull(result.getInvitations().get(0).getInviteeName());
        assertNull(result.getInvitations().get(0).getInviteePhone());
    }

    @Test
    void listSent_WhenStatusNull_ShouldDefaultToPending() {
        when(invitationRepository.findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);

        var result = invitationService.listSentInvitations(inviterId, null, 0, 10);

        assertNotNull(result);
        verify(invitationRepository).findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any());
    }

    @Test
    void listSent_WhenStatusBlank_ShouldDefaultToPending() {
        when(invitationRepository.findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);

        var result = invitationService.listSentInvitations(inviterId, "   ", 0, 10);

        assertNotNull(result);
        verify(invitationRepository).findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any());
    }

    @Test
    void listSent_WhenSizeExceeds50_ShouldThrowInv0020() {
        AppException ex = assertThrows(AppException.class,
                () -> invitationService.listSentInvitations(inviterId, "PENDING", 0, 51));
        assertEquals(InvitationErrorCode.INV_0020, ex.getErrorCode());
        verify(invitationRepository, never()).findByInviterIdAndInvitationStatus(any(), any(), any());
    }

    @Test
    void listSent_WhenSizeIsZeroOrNegative_ShouldNormalizeTo10() {
        when(invitationRepository.findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);

        var result0 = invitationService.listSentInvitations(inviterId, "PENDING", 0, 0);
        assertNotNull(result0);

        var resultNeg = invitationService.listSentInvitations(inviterId, "PENDING", 0, -5);
        assertNotNull(resultNeg);
    }

    @Test
    void listSent_WhenPageIsNegative_ShouldNormalizeTo0() {
        when(invitationRepository.findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);

        var result = invitationService.listSentInvitations(inviterId, "PENDING", -3, 10);

        assertNotNull(result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(invitationRepository).findByInviterIdAndInvitationStatus(eq(inviterId), eq("PENDING"), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    // ==================== Group 6: listReceivedInvitations() ====================

    @Test
    void listReceived_WhenHappyPath_ShouldReturnItemsWithInviterNames() {
        TrxInvitation received = TrxInvitation.builder()
                .invitationId(UUID.randomUUID())
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .invitationStatus("PENDING")
                .build();
        when(invitationRepository.findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any())).thenReturn(pageOf(received));
        when(userRepository.findAllById(any())).thenReturn(List.of(inviter));
        when(invitationRepository.countByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(0L);

        var result = invitationService.listReceivedInvitations(inviteeId, "PENDING", 0, 10);

        assertEquals(1, result.getInvitations().size());
        assertEquals("Inviter Name", result.getInvitations().get(0).getInviterName());
        assertEquals("+628111111111", result.getInvitations().get(0).getInviterPhone());
        assertEquals(0L, result.getPendingCount());
    }

    @Test
    void listReceived_WhenInviterMissingInBatch_ShouldReturnNullNameAndPhone() {
        TrxInvitation received = TrxInvitation.builder()
                .invitationId(UUID.randomUUID())
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .invitationStatus("PENDING")
                .build();
        when(invitationRepository.findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any())).thenReturn(pageOf(received));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(0L);

        var result = invitationService.listReceivedInvitations(inviteeId, "PENDING", 0, 10);

        assertNull(result.getInvitations().get(0).getInviterName());
        assertNull(result.getInvitations().get(0).getInviterPhone());
    }

    @Test
    void listReceived_WhenStatusNull_ShouldDefaultToPending() {
        when(invitationRepository.findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(0L);

        var result = invitationService.listReceivedInvitations(inviteeId, null, 0, 10);

        assertNotNull(result);
        verify(invitationRepository).findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any());
    }

    @Test
    void listReceived_WhenStatusBlank_ShouldDefaultToPending() {
        when(invitationRepository.findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(0L);

        var result = invitationService.listReceivedInvitations(inviteeId, "", 0, 10);

        assertNotNull(result);
        verify(invitationRepository).findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any());
    }

    @Test
    void listReceived_WhenSizeExceeds50_ShouldThrowInv0020() {
        AppException ex = assertThrows(AppException.class,
                () -> invitationService.listReceivedInvitations(inviteeId, "PENDING", 0, 51));
        assertEquals(InvitationErrorCode.INV_0020, ex.getErrorCode());
        verify(invitationRepository, never()).findByInviteeIdAndInvitationStatus(any(), any(), any());
    }

    @Test
    void listReceived_WhenSizeIsZeroOrNegative_ShouldNormalizeTo10() {
        when(invitationRepository.findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(0L);

        assertNotNull(invitationService.listReceivedInvitations(inviteeId, "PENDING", 0, 0));
        assertNotNull(invitationService.listReceivedInvitations(inviteeId, "PENDING", 0, -5));
    }

    @Test
    void listReceived_WhenPageIsNegative_ShouldNormalizeTo0() {
        when(invitationRepository.findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), any())).thenReturn(pageOf());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(invitationRepository.countByInviteeIdAndInvitationStatus(inviteeId, "PENDING")).thenReturn(0L);

        var result = invitationService.listReceivedInvitations(inviteeId, "PENDING", -2, 10);

        assertNotNull(result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(invitationRepository).findByInviteeIdAndInvitationStatus(eq(inviteeId), eq("PENDING"), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    // ==================== Helper for Page<TrxInvitation> mocking ====================

    private static Page<TrxInvitation> pageOf(TrxInvitation... items) {
        java.util.List<TrxInvitation> list = items.length == 0 ? java.util.List.of() : java.util.Arrays.asList(items);
        int pageSize = Math.max(items.length, 1);
        return new PageImpl<>(list, PageRequest.of(0, pageSize), list.size());
    }
}
