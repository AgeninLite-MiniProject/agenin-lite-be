package com.indivaragroup.ageninlite.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class LoginRequestDto {
    @NotBlank(message = "AUTH_0003")
    @Size(max = 20, message = "AUTH_0003")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "AUTH_0005")
    @JsonProperty("phone_number")
    private String phoneNumber;

    @NotBlank(message = "AUTH_0003")
    @Size(min = 8, max = 15, message = "AUTH_0002")
    @JsonProperty("password")
    private String password;
}
