package com.indivaragroup.ageninlite.dto.auth;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
public class LoginResponseDto {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("role")
    private String role;
}
