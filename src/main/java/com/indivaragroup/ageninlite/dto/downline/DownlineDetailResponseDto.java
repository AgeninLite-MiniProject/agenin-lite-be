package com.indivaragroup.ageninlite.dto.downline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DownlineDetailResponseDto {
    private AgentDetailDto agentDetail;

    @JsonProperty("profit_income_from_agent")
    private BigDecimal profitIncomeFromAgent;

    private List<DownlineTransactionHistoryDto> content;

    private long totalElements;
    private int totalPages;
}
