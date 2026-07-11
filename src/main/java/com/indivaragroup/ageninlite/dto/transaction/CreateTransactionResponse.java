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
public class CreateTransactionResponse {
    private UUID trxId;
    private UUID userId;
    private BigDecimal totalAmount;
    private BigDecimal totalProfit;
    private String trxStatus;
    private String description;
    private LocalDateTime createdAt;

    private List<TransactionItemResponse> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionItemResponse {
        private UUID itemId;
        private UUID productId;
        private String productName;   // Joined from mst_products
        private Integer quantity;
        private BigDecimal itemAmount;
        private BigDecimal profit;
    }
}
