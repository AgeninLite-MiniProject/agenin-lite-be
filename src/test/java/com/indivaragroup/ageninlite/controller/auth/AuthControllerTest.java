package com.indivaragroup.ageninlite.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.dto.auth.LoginRequestDto;
import com.indivaragroup.ageninlite.dto.auth.LoginResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RegisterResponseDto;
import com.indivaragroup.ageninlite.dto.auth.RefreshRequestDto;
import com.indivaragroup.ageninlite.dto.auth.RefreshResponseDto;
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
        
        RegisterResponseDto response = RegisterResponseDto.builder()
                .userId(UUID.randomUUID())
                .name("Budi")
                .userStatus("PASSIVE")
                .build();
                
        String jsonRequest = "{\"name\":\"Budi\", \"phone_number\":\"+6281234567890\", \"email\":\"budi@mail.com\", \"password\":\"password123\"}";

        when(authService.register(any(RegisterRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").exists())
                .andExpect(jsonPath("$.name").value("Budi"))
                .andExpect(jsonPath("$.user_status").value("PASSIVE"));
    }

    @Test
    void register_WhenValidationFails_ShouldReturn400() throws Exception {
        // Name is missing, phone is blank, etc
        String jsonRequest = "{\"name\":\"Budi\", \"phoneNumber\":\"\", \"email\":\"budi@mail.com\", \"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_ShouldReturn200() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        request.setPhoneNumber("+6281234567890");
        
        LoginResponseDto response = LoginResponseDto.builder()
                .accessToken("access_token")
                .refreshToken("refresh_token")
                .tokenType("Bearer")
                .build();
                
        String jsonRequest = "{\"phone_number\":\"+6281234567890\", \"password\":\"password123\"}";

        when(authService.login(any(LoginRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access_token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh_token"));
    }

    @Test
    void login_WhenServiceThrowsException_ShouldReturnError() throws Exception {
        LoginRequestDto request = new LoginRequestDto();
        request.setPhoneNumber("+6281234567890");
        String jsonRequest = "{\"phone_number\":\"+6281234567890\", \"password\":\"wrongpassword\"}";

        when(authService.login(any(LoginRequestDto.class)))
                .thenThrow(new com.indivaragroup.ageninlite.common.exception.AppException(
                        com.indivaragroup.ageninlite.common.exception.code.AuthErrorCode.AUTH_0010));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_ShouldReturn200() throws Exception {
        RefreshRequestDto request = new RefreshRequestDto();
        
        RefreshResponseDto response = RefreshResponseDto.builder()
                .accessToken("new_access_token")
                .refreshToken("new_refresh_token")
                .tokenType("Bearer")
                .expiresIn(900)
                .build();
                
        String jsonRequest = "{\"refresh_token\":\"valid_refresh_token\"}";

        when(authService.refresh(any(RefreshRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new_access_token"))
                .andExpect(jsonPath("$.refresh_token").value("new_refresh_token"));
    }
}
