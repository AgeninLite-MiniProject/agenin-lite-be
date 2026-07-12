package com.indivaragroup.ageninlite.dto.auth;

import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RefreshResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Integer expiresIn;

}
