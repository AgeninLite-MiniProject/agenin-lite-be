package com.indivaragroup.ageninlite.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentCommisionDto {

    @JsonProperty("commission_id")
    private UUID commissionId;

    @JsonProperty("commission_type")
    private String commissionType;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("commissiom_amount")
    private BigDecimal commissionAmount;

    @JsonProperty("user_name")
    private String fromUserName;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

}
