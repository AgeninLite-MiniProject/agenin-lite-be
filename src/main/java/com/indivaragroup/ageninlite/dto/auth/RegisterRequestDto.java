package com.indivaragroup.ageninlite.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class RegisterRequestDto {

    @NotBlank(message = "AUTH_0003")
    @Size(max = 100, message = "AUTH_0003")
    @JsonProperty("name")
    private String name;

    @NotBlank(message = "AUTH_0003")
    @Size(max = 20, message = "AUTH_0003")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "AUTH_0005")
    @JsonProperty("phone_number")
    private String phoneNumber;

    @Size(max = 100, message = "AUTH_0003")
    @JsonProperty("email")
    private String email;

    @NotBlank(message = "AUTH_0003")
    @Size(min = 8, max = 15, message = "AUTH_0002")
    @JsonProperty("password")
    private String password;

    @Size(max = 10, message = "AUTH_0003")
    @JsonProperty("referral_code")
    private String referralCode;
}
