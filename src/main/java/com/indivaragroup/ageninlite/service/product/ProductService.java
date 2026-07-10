package com.indivaragroup.ageninlite.service.product;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.ProductErrorCode;
import com.indivaragroup.ageninlite.dto.product.ProductCreateRequestDto;
import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.dto.product.ProductUpdateRequestDto;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponseDto createProduct(ProductCreateRequestDto request) {
        log.info("Process create product with name: {}", request.getProduct_name());

        MstProduct newProduct = MstProduct.builder()
                .productName(request.getProduct_name())
                .costPrice(request.getCost_price())
                .sellingPrice(request.getSelling_price())
                .agentFee(request.getAgent_fee())
                .superAgentFee(request.getSuper_agent_fee())
                .build();

        MstProduct savedProduct = productRepository.save(newProduct);

        return ProductResponseDto.builder()
                .product_id(savedProduct.getProductId())
                .product_name(savedProduct.getProductName())
                .product_status(savedProduct.getProductStatus())
                .message("Product created successfully")
                .build();
    }

    @Transactional
    public ProductResponseDto updateProduct(UUID productId, ProductUpdateRequestDto request) {
        log.info("Process update product with ID: {}", productId);

        MstProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRD_0002));

        if (request.getProduct_name() != null) {
            product.setProductName(request.getProduct_name());
        }
        if (request.getSelling_price() != null) {
            product.setSellingPrice(request.getSelling_price());
        }
        if (request.getProduct_status() != null) {
            product.setProductStatus(request.getProduct_status());
        }

        MstProduct updatedProduct = productRepository.save(product);

        return ProductResponseDto.builder()
                .product_id(updatedProduct.getProductId())
                .product_name(updatedProduct.getProductName())
                .product_status(updatedProduct.getProductStatus())
                .message("Product updated successfully")
                .build();
    }
    @Transactional
    public ProductResponseDto setProductInactive(UUID productId) {
        log.info("Process set inactive for product with ID: {}", productId);

        MstProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRD_0002));

        product.setProductStatus("INACTIVE");
        productRepository.save(product);

        return ProductResponseDto.builder()
                .product_id(product.getProductId())
                .product_name(product.getProductName())
                .product_status(product.getProductStatus())
                .message("Product set to inactive successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getAllProducts() {
        log.info("Process get all products");

        return productRepository.findAll().stream()
                .map(product -> ProductResponseDto.builder()
                        .product_id(product.getProductId())
                        .product_name(product.getProductName())
                        .product_status(product.getProductStatus())
                        .message("Success")
                        .build())
                .toList();
    }
}
