package com.indivaragroup.ageninlite.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.dto.admin.AdminDashboardResponseDto;
import com.indivaragroup.ageninlite.service.admin.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminDashboardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDashboardOverview_Success() throws Exception {
        AdminDashboardResponseDto responseDto = AdminDashboardResponseDto.builder()
                .total_users(100L)
                .active_agents(80L)
                .total_transactions(50L)
                .total_products(15L)
                .recent_activities(new ArrayList<>())
                .build();

        when(adminDashboardService.getDashboardOverview()).thenReturn(responseDto);

        mockMvc.perform(get("/api/admin/dashboard")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_users").value(100))
                .andExpect(jsonPath("$.active_agents").value(80))
                .andExpect(jsonPath("$.total_transactions").value(50))
                .andExpect(jsonPath("$.total_products").value(15));
    }
}
