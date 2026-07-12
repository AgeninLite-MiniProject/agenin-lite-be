package com.indivaragroup.ageninlite.dto.transaction;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteTransactionResponse {

    private UUID transactionId;
    private String trxStatus;
    private LocalDateTime completedAt;
    private String productName;
    private BigDecimal amount;
    private BigDecimal profit;
    private Integer commissionsCreated;

    private String superAgentName;

    private List<LineCommission> commissions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineCommission {
        private UUID itemId;
        private String productName;
        private BigDecimal profit;
        private BigDecimal agentFeePercentage;
        private BigDecimal agentFeeAmount;
        private BigDecimal superAgentFeePercentage;
        private BigDecimal superAgentFeeAmount;
    }
}
