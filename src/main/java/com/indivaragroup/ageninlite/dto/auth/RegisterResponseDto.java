package com.indivaragroup.ageninlite.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class RegisterResponseDto {
    private UUID id;
    private String name;
    private String phone;
    private String email;
    private String referralCode;
    private String status;
    private String message;
}
