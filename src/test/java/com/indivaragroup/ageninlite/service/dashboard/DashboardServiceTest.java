package com.indivaragroup.ageninlite.service.dashboard;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.dto.dashboard.*;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.invitation.TrxInvitationRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrxCommissionRepository trxCommissionRepository;

    @Mock
    private TrxInvitationRepository trxInvitationRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private UUID userId;
    private UUID uplineId;
    private MstUser user;
    private MstUser upline;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        uplineId = UUID.randomUUID();

        upline = MstUser.builder()
                .userId(uplineId)
                .userName("Upline User")
                .build();

        user = MstUser.builder()
                .userId(userId)
                .userStatus("ACTIVE")
                .userName("Test User")
                .phoneNumber("08123456789")
                .referralCode("REF123")
                .referredBy(uplineId)
                .build();
    }

    @Test
    void getDashboardData_Success_WithUpline() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(uplineId)).thenReturn(Optional.of(upline));

        BigDecimal agentFee = new BigDecimal("100000.00");
        BigDecimal superAgentFee = new BigDecimal("50000.00");
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "AGENT_FEE"))
                .thenReturn(agentFee);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "SUPER_AGENT_FEE"))
                .thenReturn(superAgentFee);

        List<DownlinerDto> downliners = List.of(
                DownlinerDto.builder().userId(UUID.randomUUID()).userName("Downliner 1").phoneNumber("08111").build()
        );
        when(userRepository.findDirectDownlinersByUserId(userId)).thenReturn(downliners);

        when(trxInvitationRepository.countByInviterIdAndInvitationStatus(userId, "PENDING")).thenReturn(5L);

        List<PendingInvitationDto> pendingReceived = List.of(
                PendingInvitationDto.builder().inviterId(UUID.randomUUID()).inviterName("Inviter 1").createdAt(LocalDateTime.now()).build()
        );
        when(trxInvitationRepository.findPendingInvitationsReceivedByUserId(userId)).thenReturn(pendingReceived);

        List<RecentCommissionDto> recentCommissions = List.of(
                RecentCommissionDto.builder().commissionId(UUID.randomUUID()).commissionType("AGENT_FEE").commissionAmount(agentFee).build()
        );
        when(trxCommissionRepository.findRecentCommissionsDtoByBeneficiaryId(eq(userId), any(Pageable.class)))
                .thenReturn(recentCommissions);

        // Act
        DashboardResponseDto response = dashboardService.getDashboardData(userId);

        // Assert
        assertNotNull(response);
        assertEquals(userId, response.getUserSummaryDto().getUserId());
        assertEquals("Test User", response.getUserSummaryDto().getUserName());
        assertEquals("Upline User", response.getUserSummaryDto().getReferralByName());
        assertEquals(agentFee, response.getTotalAgentFee());
        assertEquals(superAgentFee, response.getTotalSuperAgentFee());
        assertEquals(new BigDecimal("150000.00"), response.getTotalCommission());
        assertEquals(1, response.getDownlinerDtos().size());
        assertEquals(5, response.getPendingInvitationsSent());
        assertEquals(1, response.getPendingInvitationsReceived().size());
        assertEquals(1, response.getRecentCommissionDtos().size());

        verify(userRepository).findById(userId);
        verify(userRepository).findById(uplineId);
        verify(trxCommissionRepository).sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "AGENT_FEE");
        verify(trxCommissionRepository).sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "SUPER_AGENT_FEE");
        verify(userRepository).findDirectDownlinersByUserId(userId);
        verify(trxInvitationRepository).countByInviterIdAndInvitationStatus(userId, "PENDING");
        verify(trxInvitationRepository).findPendingInvitationsReceivedByUserId(userId);
        verify(trxCommissionRepository).findRecentCommissionsDtoByBeneficiaryId(eq(userId), any(Pageable.class));
    }

    @Test
    void getDashboardData_Success_WithoutUpline() {
        // Arrange
        user.setReferredBy(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "AGENT_FEE"))
                .thenReturn(BigDecimal.ZERO);
        when(trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "SUPER_AGENT_FEE"))
                .thenReturn(BigDecimal.ZERO);
        when(userRepository.findDirectDownlinersByUserId(userId)).thenReturn(List.of());
        when(trxInvitationRepository.countByInviterIdAndInvitationStatus(userId, "PENDING")).thenReturn(0L);
        when(trxInvitationRepository.findPendingInvitationsReceivedByUserId(userId)).thenReturn(List.of());
        when(trxCommissionRepository.findRecentCommissionsDtoByBeneficiaryId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());

        // Act
        DashboardResponseDto response = dashboardService.getDashboardData(userId);

        // Assert
        assertNotNull(response);
        assertNull(response.getUserSummaryDto().getReferralByName());
        assertEquals(BigDecimal.ZERO, response.getTotalCommission());
    }

    @Test
    void getDashboardData_UserNotFound_ThrowsAppException() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> {
            dashboardService.getDashboardData(userId);
        });

        assertEquals(UserErrorCode.USR_0001, exception.getErrorCode());
        verify(userRepository).findById(userId);
        verifyNoInteractions(trxCommissionRepository);
        verifyNoInteractions(trxInvitationRepository);
    }
}
