package com.indivaragroup.ageninlite.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponseDto {
    @JsonProperty("user")
    private UserSummaryDto userSummaryDto;

    @JsonProperty("total_agent_fee")
    private BigDecimal totalAgentFee;
    @JsonProperty("total_super_agent_fee")
    private BigDecimal totalSuperAgentFee;
    @JsonProperty("total_commission")
    private BigDecimal totalCommission;

    @JsonProperty("downliners")
    private List<DownlinerDto> downlinerDtos;

    private int pendingInvitationsSent;
    private List<PendingInvitationDto> pendingInvitationsReceived;

    @JsonProperty("recent_commissions")
    private List<RecentCommissionDto> recentCommissionDtos;
}
