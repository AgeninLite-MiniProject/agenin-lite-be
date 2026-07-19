package com.indivaragroup.ageninlite.controller.dashboard;

import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.dto.dashboard.DashboardResponseDto;
import com.indivaragroup.ageninlite.dto.dashboard.UserSummaryDto;
import com.indivaragroup.ageninlite.service.dashboard.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController controller;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDashboard_Success() throws Exception {
        // Arrange
        DashboardResponseDto mockResponse = DashboardResponseDto.builder()
                .userSummaryDto(UserSummaryDto.builder()
                        .userId(userId)
                        .userName("Naufal")
                        .email("naufal@example.com")
                        .build())
                .totalAgentFee(new BigDecimal("1000"))
                .totalSuperAgentFee(new BigDecimal("500"))
                .totalCommission(new BigDecimal("1500"))
                .downlinerDtos(List.of())
                .pendingInvitationsSent(0)
                .pendingInvitationsReceived(List.of())
                .recentCommissionDtos(List.of())
                .build();

        when(dashboardService.getDashboardData(userId)).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dashboard data received successfully"))
                .andExpect(jsonPath("$.data.total_commission").value(1500));

        verify(dashboardService).getDashboardData(userId);
    }

    @Test
    void getDashboard_WithInvalidUUID_ShouldReturn400() throws Exception {
        // Arrange
        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "not-a-uuid",
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Act & Assert
        // UUID.fromString("not-a-uuid") will throw IllegalArgumentException.
        // Assuming GlobalExceptionHandler handles IllegalArgumentException with 400 Bad Request.
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isBadRequest());
    }
}
