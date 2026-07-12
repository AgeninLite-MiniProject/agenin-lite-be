package com.indivaragroup.ageninlite.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RegisterRequestDto {

    @NotBlank(message = "AUTH_0003")
    @Size(max = 100, message = "AUTH_0003")
    private String name;

    @NotBlank(message = "AUTH_0003")
    @Size(max = 20, message = "AUTH_0003")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "AUTH_0005")
    private String phoneNumber;

    @Size(max = 100, message = "AUTH_0003")
    private String email;

    @NotBlank(message = "AUTH_0003")
    @Size(min = 8, max = 15, message = "AUTH_0002")
    private String password;

    @Size(max = 10, message = "AUTH_0003")
    private String referralCode;
}
