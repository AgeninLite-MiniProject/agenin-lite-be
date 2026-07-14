package com.indivaragroup.ageninlite.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionListResponseV2 {
    private List<TransactionListItemV2Dto> transactions;
    private BigDecimal totalCommission;
    private long completedCount;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
