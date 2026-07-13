package com.indivaragroup.ageninlite.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequestDto {

    @NotBlank(message = "AUTH_0030")
    @JsonProperty("refresh_token")
    private String refreshToken;

}
