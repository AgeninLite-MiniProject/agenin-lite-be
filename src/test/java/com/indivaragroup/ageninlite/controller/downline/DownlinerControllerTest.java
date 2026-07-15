package com.indivaragroup.ageninlite.controller.downline;

import com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler;
import com.indivaragroup.ageninlite.dto.downline.AgentDetailDto;
import com.indivaragroup.ageninlite.dto.downline.DownlineDetailResponseDto;
import com.indivaragroup.ageninlite.dto.downline.DownlineTransactionHistoryDto;
import com.indivaragroup.ageninlite.service.downline.DownlinerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DownlinerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DownlinerService downlinerService;

    @InjectMocks
    private DownlinerController controller;

    private UUID requesterId;
    private UUID downlinerId;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        downlinerId = UUID.randomUUID();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                requesterId.toString(),
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
    void getDownlinerDetail_Success() throws Exception {
        // Arrange
        AgentDetailDto agent = AgentDetailDto.builder()
                .userId(downlinerId)
                .userName("John Doe")
                .phoneNumber("0812345678")
                .email("john@example.com")
                .referralCode("JD123")
                .joinedAt(LocalDate.now().toString())
                .lastTransactionAt(LocalDateTime.now().toString())
                .status("ACTIVE")
                .build();

        com.indivaragroup.ageninlite.dto.downline.DownlineTransactionItemDto item = com.indivaragroup.ageninlite.dto.downline.DownlineTransactionItemDto.builder()
                .productName("Produk A")
                .quantity(2)
                .amount(new BigDecimal("200000"))
                .commissionEarned(new BigDecimal("10000"))
                .build();

        DownlineTransactionHistoryDto history = DownlineTransactionHistoryDto.builder()
                .trxId(UUID.randomUUID())
                .items(List.of(item))
                .amount(new BigDecimal("200000"))
                .status("COMPLETED")
                .completedAt(LocalDateTime.now())
                .totalCommissionEarned(new BigDecimal("10000"))
                .build();

        DownlineDetailResponseDto mockResponse = DownlineDetailResponseDto.builder()
                .agentDetail(agent)
                .profitIncomeFromAgent(new BigDecimal("50000"))
                .content(List.of(history))
                .totalElements(1)
                .totalPages(1)
                .build();

        when(downlinerService.getDownlineDetail(requesterId, downlinerId, PageRequest.of(0, 20)))
                .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/api/downliners/{id}", downlinerId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Downliner detail fetched successfully"))
                .andExpect(jsonPath("$.data.profit_income_from_agent").value(50000))
                .andExpect(jsonPath("$.data.agentDetail.user_id").value(downlinerId.toString()))
                .andExpect(jsonPath("$.data.content[0].items[0].product_name").value("Produk A"));

        verify(downlinerService).getDownlineDetail(requesterId, downlinerId, PageRequest.of(0, 20));
    }
}
