package com.indivaragroup.ageninlite.dto.downline;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DownlineTransactionItemDto {

    @JsonProperty("product_name")
    private String productName;
    private int quantity;
    private BigDecimal amount;

    @JsonProperty("commission_earned")
    private BigDecimal commissionEarned;
}
