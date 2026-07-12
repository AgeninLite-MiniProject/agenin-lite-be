package com.indivaragroup.ageninlite.dto.auth;

import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RegisterResponseDto {
    private UUID userId;
    private String name;
    private String phoneNumber;
    private String referralCode;
    private UUID referredBy;
    private String role;
    private String userStatus;
    private String message;
}
