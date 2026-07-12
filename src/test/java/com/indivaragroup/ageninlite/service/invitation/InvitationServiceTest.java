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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private TrxInvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InvitationService invitationService;

    private UUID inviterId;
    private UUID inviteeId;
    private UUID otherInviterId;
    private MstUser inviter;
    private MstUser invitee;
    private SendInvitationRequest request;
    private TrxInvitation otherPendingInvitation;

    @BeforeEach
    void setUp() {
        inviterId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();
        otherInviterId = UUID.randomUUID();

        inviter = MstUser.builder()
                .userId(inviterId)
                .userName("Inviter Name")
                .phoneNumber("+628111")
                .passwordHash("hash")
                .role("AGENT")
                .userStatus("ACTIVE")
                .isDeleted(false)
                .build();

        invitee = MstUser.builder()
                .userId(inviteeId)
                .userName("Invitee Name")
                .phoneNumber("+628222")
                .passwordHash("hash")
                .role("AGENT")
                .userStatus("PASSIVE")
                .isDeleted(false)
                .referredBy(null)
                .build();

        request = new SendInvitationRequest(inviteeId);

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
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
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
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
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
        SendInvitationRequest selfRequest = new SendInvitationRequest(inviterId);

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, selfRequest));
        assertEquals(InvitationErrorCode.INV_0001, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviteeHasUpline_ShouldThrowInv0004() {
        invitee.setReferredBy(UUID.randomUUID());
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0004, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviterHas3Pending_ShouldThrowInv0005() {
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(3L);

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0005, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenDuplicatePending_ShouldThrowInv0003() {
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(invitationRepository.countByInviterIdAndInvitationStatus(inviterId, "PENDING")).thenReturn(0L);
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0003, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviteeNotFound_ShouldThrowInv0006() {
        when(userRepository.findById(inviteeId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0006, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void send_Invitation_WhenInviteeDeleted_ShouldThrowInv0007() {
        invitee.setDeleted(true);
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));

        AppException ex = assertThrows(AppException.class, () -> invitationService.sendInvitation(inviterId, request));
        assertEquals(InvitationErrorCode.INV_0007, ex.getErrorCode());
        verify(invitationRepository, never()).save(any());
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
    void accept_Invitation_WhenInviterAt10Downliners_ShouldExpireAndThrowInv0010() {
        when(invitationRepository.findByInviterIdAndInviteeId(inviterId, inviteeId)).thenReturn(Optional.of(buildInvitation("PENDING")));
        when(userRepository.findById(inviteeId)).thenReturn(Optional.of(invitee));
        when(userRepository.countByReferredBy(inviterId)).thenReturn(10);
        when(invitationRepository.save(any(TrxInvitation.class))).thenAnswer(i -> i.getArguments()[0]);

        AppException ex = assertThrows(AppException.class, () -> invitationService.acceptInvitation(inviterId, inviteeId));
        assertEquals(InvitationErrorCode.INV_0010, ex.getErrorCode());
        ArgumentCaptor<TrxInvitation> captor = ArgumentCaptor.forClass(TrxInvitation.class);
        verify(invitationRepository).save(captor.capture());
        assertEquals("EXPIRED", captor.getValue().getInvitationStatus());
        verify(userRepository, never()).save(any(MstUser.class));
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
}
