package com.indivaragroup.ageninlite.controller.product;

import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController controller;

    private UUID productId;
    private ProductResponseDto mockResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(
                    new com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler()
                )
                .build();

        productId = UUID.randomUUID();
        mockResponse = ProductResponseDto.builder()
                .product_id(productId)
                .product_name("Teh Pucuk Harum 1 Karton")
                .cost_price(new BigDecimal("40000.00"))
                .selling_price(new BigDecimal("50000.00"))
                .agent_fee(new BigDecimal("10.00"))
                .super_agent_fee(new BigDecimal("5.00"))
                .product_status("ACTIVE")
                .message("Success")
                .build();
    }

    @Test
    void getActiveProducts_Success() throws Exception {
        when(productService.getActiveProducts()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].product_name").value("Teh Pucuk Harum 1 Karton"))
                .andExpect(jsonPath("$[0].product_status").value("ACTIVE"));
    }

    @Test
    void getActiveProducts_EmptyList() throws Exception {
        when(productService.getActiveProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0));
    }
}
