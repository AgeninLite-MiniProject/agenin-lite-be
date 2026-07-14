package com.indivaragroup.ageninlite.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionItemLineDto {
    private UUID itemId;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private BigDecimal itemAmount;
    private BigDecimal profit;
}
