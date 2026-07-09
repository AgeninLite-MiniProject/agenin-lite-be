package com.indivaragroup.ageninlite.dto.product;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductUpdateRequestDto {
    private String product_name;
    private BigDecimal selling_price;
    private String product_status;
}