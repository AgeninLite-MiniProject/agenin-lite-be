package com.indivaragroup.ageninlite.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class RefreshRequestDto {
    @NotBlank(message = "AUTH_0003")
    @JsonProperty("refresh_token")
    private String refreshToken;
}
