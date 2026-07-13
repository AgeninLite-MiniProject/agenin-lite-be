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
public class TransactionDetailResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private BigDecimal amount;
    private BigDecimal profit;
    private BigDecimal agentFeeAmount;
    private BigDecimal superAgentFeeAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String description;
    private UUID sellerId;
    private String sellerName;
    private List<CreateTransactionResponse.TransactionItemResponse> items;
}
