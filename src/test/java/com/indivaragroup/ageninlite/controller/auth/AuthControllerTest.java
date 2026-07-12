package com.indivaragroup.ageninlite.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.dto.auth.LoginRequestDto;
import com.indivaragroup.ageninlite.dto.auth.LoginResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterResponseDto;
import com.indivaragroup.ageninlite.service.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_ShouldReturn201() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto();
        request.setName("Budi");
        request.setPhoneNumber("+6281234567890");
        request.setEmail("budi@mail.com");
        request.setPassword("password123");

        RegisterResponseDto response = RegisterResponseDto.builder()
                .userId(UUID.randomUUID())
                .name("Budi")
                .userStatus("PASSIVE")
                .build();

        when(authService.register(any(RegisterRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration Successfull"));
    }

    @Test
    void register_WhenValidationFails_ShouldReturn400() throws Exception {
        // Name is missing, phone is blank, etc
        RegisterRequestDto request = new RegisterRequestDto();
        request.setPhoneNumber(""); 

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_ShouldReturn200() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        request.setPhoneNumber("+6281234567890");
        request.setPassword("password123");

        LoginResponseDto response = LoginResponseDto.builder()
                .accessToken("access_token")
                .refreshToken("refresh_token")
                .tokenType("Bearer")
                .build();

        when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login Successfull"));
    }

    @Test
    void login_WhenServiceThrowsException_ShouldReturnError() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        request.setPhoneNumber("+6281234567890");
        request.setPassword("wrongpassword");

        when(authService.login(any(LoginRequestDto.class)))
                .thenThrow(new com.indivaragroup.ageninlite.common.exception.AppException(
                        com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode.AUTH_0010));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
