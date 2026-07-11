package com.indivaragroup.ageninlite.dto.product;

import jakarta.validation.constraints.Min;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductUpdateRequestDto {
    private String product_name;
    
    @Min(value = 0, message = "PRD_0008")
    private BigDecimal selling_price;

    @Min(value = 0, message = "PRD_0008")
    private BigDecimal cost_price;

    @Min(value = 0, message = "PRD_0008")
    private BigDecimal agent_fee;

    @Min(value = 0, message = "PRD_0008")
    private BigDecimal super_agent_fee;

    private String product_status;
}