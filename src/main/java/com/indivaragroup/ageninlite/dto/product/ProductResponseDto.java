package com.indivaragroup.ageninlite.dto.product;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;
import java.math.BigDecimal;

@Data
@Builder
public class ProductResponseDto {
    private UUID product_id;
    private String product_name;
    private BigDecimal cost_price;
    private BigDecimal selling_price;
    private BigDecimal agent_fee;
    private BigDecimal super_agent_fee;
    private String product_status;
    private String message;
}
