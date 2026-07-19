package com.indivaragroup.ageninlite.dto.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTransactionRequest {
    @NotEmpty(message = "items must not be empty")
    @Size(max = 50, message = "items must not exceed 50 line items per transaction")
    @Valid
    private List<CreateTransactionItem> items;

    @Size(max = 255, message = "description must not exceed 255 characters")
    private String description;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateTransactionItem {

        @NotNull(message = "productId is required")
        private UUID productId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be greater than 0")
        private Integer quantity;
    }
}
