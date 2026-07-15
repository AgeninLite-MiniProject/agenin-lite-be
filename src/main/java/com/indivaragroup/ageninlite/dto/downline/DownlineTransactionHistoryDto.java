package com.indivaragroup.ageninlite.dto.downline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DownlineTransactionHistoryDto {

    private UUID trxId;

    private List<DownlineTransactionItemDto> items;

    private BigDecimal amount;
    private String status;

    @JsonProperty("completed_at")
    private LocalDateTime completedAt;

    @JsonProperty("total_commission_earned")
    private BigDecimal totalCommissionEarned;

}
