package com.indivaragroup.ageninlite.service.product;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.ProductErrorCode;
import com.indivaragroup.ageninlite.dto.product.ProductCreateRequestDto;
import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.dto.product.ProductUpdateRequestDto;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private UUID productId;
    private MstProduct mockProduct;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        mockProduct = MstProduct.builder()
                .productId(productId)
                .productName("Teh Pucuk")
                .costPrice(new BigDecimal("40000.00"))
                .sellingPrice(new BigDecimal("50000.00"))
                .agentFee(new BigDecimal("10.00"))
                .superAgentFee(new BigDecimal("5.00"))
                .productStatus("ACTIVE")
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

        when(productRepository.save(any(MstProduct.class))).thenReturn(mockProduct);

        ProductResponseDto response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals(productId, response.getProduct_id());
        assertEquals("Teh Pucuk", response.getProduct_name());
        assertEquals("ACTIVE", response.getProduct_status());
        verify(productRepository, times(1)).save(any(MstProduct.class));
    }

    @Test
    void updateProduct_Success_AllFields() {
        ProductUpdateRequestDto request = new ProductUpdateRequestDto();
        request.setProduct_name("Kopi ABC");
        request.setCost_price(new BigDecimal("45000.00"));
        request.setSelling_price(new BigDecimal("55000.00"));
        request.setAgent_fee(new BigDecimal("15.00"));
        request.setSuper_agent_fee(new BigDecimal("7.00"));
        request.setProduct_status("INACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));
        when(productRepository.save(any(MstProduct.class))).thenReturn(mockProduct);

        ProductResponseDto response = productService.updateProduct(productId, request);

        assertNotNull(response);
        assertEquals("Product updated successfully", response.getMessage());
        assertEquals("Kopi ABC", mockProduct.getProductName());
        assertEquals(new BigDecimal("55000.00"), mockProduct.getSellingPrice());
        assertEquals("INACTIVE", mockProduct.getProductStatus());
        assertEquals(productId, response.getProduct_id());
        assertEquals("Kopi ABC", response.getProduct_name());
        assertEquals("INACTIVE", response.getProduct_status());
    }

    @Test
    void updateProduct_Success_NoFields() {
        ProductUpdateRequestDto request = new ProductUpdateRequestDto();

        when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));
        when(productRepository.save(any(MstProduct.class))).thenReturn(mockProduct);

        ProductResponseDto response = productService.updateProduct(productId, request);

        assertNotNull(response);
        assertEquals("Teh Pucuk", mockProduct.getProductName());
        assertEquals(productId, response.getProduct_id());
        assertEquals("Teh Pucuk", response.getProduct_name());
    }

    @Test
    void updateProduct_NotFound() {
        ProductUpdateRequestDto request = new ProductUpdateRequestDto();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            productService.updateProduct(productId, request);
        });

        assertEquals(ProductErrorCode.PRD_0002, exception.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void setProductInactive_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));
        when(productRepository.save(any(MstProduct.class))).thenReturn(mockProduct);

        ProductResponseDto response = productService.setProductInactive(productId);

        assertNotNull(response);
        assertEquals("Product set to inactive successfully", response.getMessage());
        assertEquals("INACTIVE", mockProduct.getProductStatus());
        assertEquals(productId, response.getProduct_id());
        assertEquals("Teh Pucuk", response.getProduct_name());
        assertEquals("INACTIVE", response.getProduct_status());
    }

    @Test
    void setProductInactive_NotFound() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () -> {
            productService.setProductInactive(productId);
        });

        assertEquals(ProductErrorCode.PRD_0002, exception.getErrorCode());
    }

    @Test
    void getAllProducts_Success() {
        when(productRepository.findAll()).thenReturn(List.of(mockProduct));

        List<ProductResponseDto> responses = productService.getAllProducts();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(productId, responses.get(0).getProduct_id());
    }
}
