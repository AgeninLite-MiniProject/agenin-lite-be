package com.indivaragroup.ageninlite.dto.auth;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
public class RegisterResponseDto {
    @JsonProperty("user_id")
    private UUID userId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    @JsonProperty("referral_code")
    private String referralCode;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("referred_by")
    private UUID referredBy;
    
    @JsonProperty("user_status")
    private String userStatus;
    
    @JsonProperty("message")
    private String message;
}
