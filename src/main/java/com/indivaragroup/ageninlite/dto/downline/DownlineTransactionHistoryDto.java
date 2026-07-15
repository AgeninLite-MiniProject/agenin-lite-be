package com.indivaragroup.ageninlite.dto.downline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DownlineTransactionHistoryDto {

    private UUID trxId;

    @JsonProperty("product_name")
    private String productName;

    private int quantity;
    private BigDecimal amount;
    private String status;

    @JsonProperty("completed_at")
    private LocalDateTime completedAt;

    @JsonProperty("commission_earned")
    private BigDecimal commissionEarned;

    @JsonProperty("super_agent_fee_amount")
    private BigDecimal superAgentFeeAmount;

}
