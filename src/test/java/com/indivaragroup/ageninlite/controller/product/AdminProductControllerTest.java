package com.indivaragroup.ageninlite.controller.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indivaragroup.ageninlite.dto.product.ProductCreateRequestDto;
import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.dto.product.ProductUpdateRequestDto;
import com.indivaragroup.ageninlite.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private AdminProductController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID productId;
    private ProductResponseDto mockResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.indivaragroup.ageninlite.common.exception.GlobalExceptionHandler())
                .build();

        productId = UUID.randomUUID();
        mockResponse = ProductResponseDto.builder()
                .product_id(productId)
                .product_name("Teh Pucuk")
                .product_status("ACTIVE")
                .message("Success")
                .build();
    }

    @Test
    void createProduct_Success() throws Exception {
        ProductCreateRequestDto request = new ProductCreateRequestDto();
        request.setProduct_name("Teh Pucuk");
        request.setCost_price(new BigDecimal("40000.00"));
        request.setSelling_price(new BigDecimal("50000.00"));
        request.setAgent_fee(new BigDecimal("10.00"));
        request.setSuper_agent_fee(new BigDecimal("5.00"));

        when(productService.createProduct(any(ProductCreateRequestDto.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product_name").value("Teh Pucuk"))
                .andExpect(jsonPath("$.product_id").value(productId.toString()));
    }

    @Test
    void createProduct_ValidationFailed_NegativePrice() throws Exception {
        ProductCreateRequestDto request = new ProductCreateRequestDto();
        request.setProduct_name("Teh Pucuk");
        request.setCost_price(new BigDecimal("-40000.00"));
        request.setSelling_price(new BigDecimal("50000.00"));
        request.setAgent_fee(new BigDecimal("10.00"));
        request.setSuper_agent_fee(new BigDecimal("5.00"));

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createProduct_ValidationFailed_MissingName() throws Exception {
        ProductCreateRequestDto request = new ProductCreateRequestDto();
        request.setCost_price(new BigDecimal("40000.00"));
        request.setSelling_price(new BigDecimal("50000.00"));
        request.setAgent_fee(new BigDecimal("10.00"));
        request.setSuper_agent_fee(new BigDecimal("5.00"));

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateProduct_Success() throws Exception {
        ProductUpdateRequestDto request = new ProductUpdateRequestDto();
        request.setProduct_name("Kopi ABC");

        when(productService.updateProduct(eq(productId), any(ProductUpdateRequestDto.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/admin/products/{productId}/update", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_name").value("Teh Pucuk"));
    }

    @Test
    void setProductInactive_Success() throws Exception {
        when(productService.setProductInactive(productId)).thenReturn(mockResponse);

        mockMvc.perform(post("/api/admin/products/{productId}/inactive", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_status").value("ACTIVE"));
    }

    @Test
    void getAllProducts_Success() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].product_name").value("Teh Pucuk"));
    }
}
