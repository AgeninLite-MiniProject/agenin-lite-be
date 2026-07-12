package com.indivaragroup.ageninlite.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class LoginRequestDto {
    @NotBlank(message = "AUTH_0003")
    @JsonProperty("phone_number")
    private String phoneNumber;

    @NotBlank(message = "AUTH_0003")
    @JsonProperty("password")
    private String password;
}
