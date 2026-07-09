package com.indivaragroup.ageninlite.controller.product;


import com.indivaragroup.ageninlite.dto.product.ProductCreateRequestDto;
import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.dto.product.ProductUpdateRequestDto;
import com.indivaragroup.ageninlite.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@Valid @RequestBody ProductCreateRequestDto request) {
        ProductResponseDto response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{productId}/update")
    public ResponseEntity<ProductResponseDto> updateProduct(@PathVariable UUID productId, @RequestBody ProductUpdateRequestDto request) {
        ProductResponseDto response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/inactive")
    public ResponseEntity<ProductResponseDto> setProductInactive(@PathVariable UUID productId) {
        ProductResponseDto response = productService.setProductInactive(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }
}