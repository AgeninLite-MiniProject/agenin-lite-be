package com.indivaragroup.ageninlite.service.user.dashboard;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.UserErrorCode;
import com.indivaragroup.ageninlite.dto.dashboard.*;
import com.indivaragroup.ageninlite.entity.MstUser;
import com.indivaragroup.ageninlite.repository.auth.UserRepository;
import com.indivaragroup.ageninlite.repository.invitation.TrxInvitationRepository;
import com.indivaragroup.ageninlite.repository.transaction.TrxCommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final TrxCommissionRepository trxCommissionRepository;
    private final TrxInvitationRepository trxInvitationRepository;

    public DashboardResponseDto getDashboardData(UUID userId) {
        // tarik data user
        MstUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(UserErrorCode.USR_0001));

        // tarik nama upline-nya
        String referredByName = null;
        if (user.getReferredBy() != null) {
            referredByName= userRepository.findById(user.getReferredBy())
                    .map(MstUser::getUserName)
                    .orElse(null);
        }

        // bikin object
        UserSummaryDto userSummary = UserSummaryDto.builder()
                .userId(user.getUserId())
                .userStatus(user.getUserStatus())
                .userName(user.getUserName())
                .phoneNumber(user.getPhoneNumber())
                .referralCode(user.getReferralCode())
                .referralByName(referredByName)
                .build();

        // hitung komisi
        BigDecimal agentFee = trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "AGENT_FEE");
        BigDecimal superAgentFee = trxCommissionRepository.sumCommissionAmountByBeneficiaryIdAndCommissionType(userId, "SUPER_AGENT_FEE");
        BigDecimal totalCommission = agentFee.add(superAgentFee);

        // ambil data downliners
        List<DownlinerDto> downliners = userRepository.findDirectDownlinersByUserId(userId);

        // ambil data undangan (sent & received)
        int pendingSent = (int) trxInvitationRepository.countByInviterIdAndInvitationStatus(userId, "PENDING");
        List<PendingInvitationDto> pendingReceived = trxInvitationRepository.findPendingInvitationsReceivedByUserId(userId);

        // limit 20 aja
        List<RecentCommissionDto> recentCommissions = trxCommissionRepository.findRecentCommissionsDtoByBeneficiaryId(userId, PageRequest.of(0,20));

        // response
        return DashboardResponseDto.builder()
                .userSummaryDto(userSummary)
                .totalAgentFee(agentFee)
                .totalSuperAgentFee(superAgentFee)
                .totalCommission(totalCommission)
                .downlinerDtos(downliners)
                .pendingInvitationsSent(pendingSent)
                .pendingInvitationsReceived(pendingReceived)
                .recentCommissionDtos(recentCommissions)
                .build();
    }
}
