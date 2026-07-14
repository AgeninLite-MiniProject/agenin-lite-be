package com.indivaragroup.ageninlite.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionListItemV2Dto {
    private UUID id;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private BigDecimal amount;
    private BigDecimal profit;
    private BigDecimal agentFeeAmount;
    private BigDecimal superAgentFeeAmount;
    private int totalQuantity;
    private List<TransactionItemLineDto> items;
}
