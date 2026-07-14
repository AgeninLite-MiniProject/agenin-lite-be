package com.indivaragroup.ageninlite.dto.downline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AgentDetailDto {

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("phone_number")
    private String phoneNumber;

    private String email;

    @JsonProperty("referral_code")
    private String referralCode;

    @JsonProperty("joined_at")
    private String joinedAt;

    @JsonProperty("last_transaction_at")
    private String lastTransactionAt;

    private String status;

}
