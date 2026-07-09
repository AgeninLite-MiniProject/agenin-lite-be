package com.indivaragroup.ageninlite.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductCreateRequestDto {
    @NotBlank(message = "Product name is mandatory")
    private String product_name;

    @NotNull(message = "Cost price is mandatory")
    private BigDecimal cost_price;

    @NotNull(message = "Selling price is mandatory")
    private BigDecimal selling_price;

    @NotNull(message = "Agent fee is mandatory")
    private BigDecimal agent_fee;

    @NotNull(message = "Super agent fee is mandatory")
    private BigDecimal super_agent_fee;
}
