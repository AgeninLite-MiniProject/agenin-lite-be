package com.indivaragroup.ageninlite.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductCreateRequestDto {
    @NotBlank(message = "PRD_0003")
    private String product_name;

    @NotNull(message = "PRD_0004")
    @Min(value = 0, message = "PRD_0008")
    private BigDecimal cost_price;

    @NotNull(message = "PRD_0005")
    @Min(value = 0, message = "PRD_0008")
    private BigDecimal selling_price;

    @NotNull(message = "PRD_0006")
    @Min(value = 0, message = "PRD_0008")
    private BigDecimal agent_fee;

    @NotNull(message = "PRD_0007")
    @Min(value = 0, message = "PRD_0008")
    private BigDecimal super_agent_fee;
}
