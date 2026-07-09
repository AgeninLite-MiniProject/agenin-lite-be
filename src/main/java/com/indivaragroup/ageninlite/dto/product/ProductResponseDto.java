package com.indivaragroup.ageninlite.dto.product;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ProductResponseDto {
    private UUID product_id;
    private String product_name;
    private String product_status;
    private String message;
}
