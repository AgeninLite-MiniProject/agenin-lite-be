package com.indivaragroup.ageninlite.controller.product;

import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.service.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getActiveProducts() {
        return ResponseEntity.ok(productService.getActiveProducts());
    }
}
