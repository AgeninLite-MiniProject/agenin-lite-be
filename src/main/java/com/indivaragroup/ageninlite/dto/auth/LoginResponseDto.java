package com.indivaragroup.ageninlite.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
}
