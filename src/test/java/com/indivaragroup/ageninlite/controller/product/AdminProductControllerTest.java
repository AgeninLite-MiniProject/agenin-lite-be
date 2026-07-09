package com.indivaragroup.ageninlite.controller.product;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private AdminProductController controller;

    private UUID productId;
    private ProductResponseDto mockResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        mockResponse = ProductResponseDto.builder()
                .product_id(productId)
                .product_name("Teh Pucuk")
                .product_status("ACTIVE")
                .message("Success")
                .build();
    }

    @Test
    void createProduct_Success() {
        ProductCreateRequestDto request = new ProductCreateRequestDto();
        request.setProduct_name("Teh Pucuk");
        request.setCost_price(new BigDecimal("40000.00"));
        request.setSelling_price(new BigDecimal("50000.00"));
        request.setAgent_fee(new BigDecimal("10.00"));
        request.setSuper_agent_fee(new BigDecimal("5.00"));

        when(productService.createProduct(any(ProductCreateRequestDto.class))).thenReturn(mockResponse);

        ResponseEntity<ProductResponseDto> responseEntity = controller.createProduct(request);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    @Test
    void updateProduct_Success() {
        ProductUpdateRequestDto request = new ProductUpdateRequestDto();
        request.setProduct_name("Kopi ABC");

        when(productService.updateProduct(eq(productId), any(ProductUpdateRequestDto.class))).thenReturn(mockResponse);

        ResponseEntity<ProductResponseDto> responseEntity = controller.updateProduct(productId, request);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    @Test
    void setProductInactive_Success() {
        when(productService.setProductInactive(productId)).thenReturn(mockResponse);

        ResponseEntity<ProductResponseDto> responseEntity = controller.setProductInactive(productId);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    @Test
    void getAllProducts_Success() {
        when(productService.getAllProducts()).thenReturn(List.of(mockResponse));

        ResponseEntity<List<ProductResponseDto>> responseEntity = controller.getAllProducts();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(1, responseEntity.getBody().size());
        assertEquals(mockResponse, responseEntity.getBody().get(0));
    }
}
