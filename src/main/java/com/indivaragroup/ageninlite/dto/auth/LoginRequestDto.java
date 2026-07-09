package com.indivaragroup.ageninlite.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @NotBlank(message = "AUTH_0003")
    private String phone;

    @NotBlank(message = "AUTH_0003")
    private String password;
}
