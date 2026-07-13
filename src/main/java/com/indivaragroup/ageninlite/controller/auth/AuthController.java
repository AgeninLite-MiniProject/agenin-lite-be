package com.indivaragroup.ageninlite.controller.auth;

import com.indivaragroup.ageninlite.dto.auth.*;
import com.indivaragroup.ageninlite.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        RegisterResponseDto response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        LoginResponseDto response = authService.login(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponseDto> refresh(@Valid @RequestBody RefreshRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponseDto> logout(@RequestHeader(value = "Authorization", required = false) String authHeader, @Valid @RequestBody LogoutRequestDto request) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new com.indivaragroup.ageninlite.common.exception.AppException(com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode.AUTH_0020);
        }

        String accessToken = authHeader.substring(7);
        authService.logout(accessToken, request);
        return ResponseEntity.ok(new LogoutResponseDto("Logout successful"));
    }
}
