package com.indivaragroup.ageninlite.service.product;

import com.indivaragroup.ageninlite.common.exception.AppException;
import com.indivaragroup.ageninlite.common.exception.code.ProductErrorCode;
import com.indivaragroup.ageninlite.dto.product.ProductCreateRequestDto;
import com.indivaragroup.ageninlite.dto.product.ProductResponseDto;
import com.indivaragroup.ageninlite.dto.product.ProductUpdateRequestDto;
import com.indivaragroup.ageninlite.entity.MstProduct;
import com.indivaragroup.ageninlite.repository.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponseDto createProduct(ProductCreateRequestDto request) {
<<<<<<< Updated upstream
=======
        log.info("Process create product with name: {}", request.getProduct_name());

        validatePricingAndFee(request.getCost_price(), request.getSelling_price(), request.getAgent_fee(), request.getSuper_agent_fee());

>>>>>>> Stashed changes
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
                .cost_price(savedProduct.getCostPrice())
                .selling_price(savedProduct.getSellingPrice())
                .agent_fee(savedProduct.getAgentFee())
                .super_agent_fee(savedProduct.getSuperAgentFee())
                .product_status(savedProduct.getProductStatus())
                .message("Product created successfully")
                .build();
    }

    @Transactional
    public ProductResponseDto updateProduct(UUID productId, ProductUpdateRequestDto request) {
        MstProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRD_0002));

        if (request.getProduct_name() != null) {
            product.setProductName(request.getProduct_name());
        }
        if (request.getSelling_price() != null) {
            product.setSellingPrice(request.getSelling_price());
        }
        if (request.getCost_price() != null) {
            product.setCostPrice(request.getCost_price());
        }
        if (request.getAgent_fee() != null) {
            product.setAgentFee(request.getAgent_fee());
        }
        if (request.getSuper_agent_fee() != null) {
            product.setSuperAgentFee(request.getSuper_agent_fee());
        }
        if (request.getProduct_status() != null) {
            product.setProductStatus(request.getProduct_status());
        }

        validatePricingAndFee(product.getCostPrice(), product.getSellingPrice(), product.getAgentFee(), product.getSuperAgentFee());

        MstProduct updatedProduct = productRepository.save(product);

        return ProductResponseDto.builder()
                .product_id(updatedProduct.getProductId())
                .product_name(updatedProduct.getProductName())
                .cost_price(updatedProduct.getCostPrice())
                .selling_price(updatedProduct.getSellingPrice())
                .agent_fee(updatedProduct.getAgentFee())
                .super_agent_fee(updatedProduct.getSuperAgentFee())
                .product_status(updatedProduct.getProductStatus())
                .message("Product updated successfully")
                .build();
    }
    @Transactional
    public ProductResponseDto setProductInactive(UUID productId) {
        MstProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRD_0002));

        product.setProductStatus("INACTIVE");
        productRepository.save(product);

        return ProductResponseDto.builder()
                .product_id(product.getProductId())
                .product_name(product.getProductName())
                .cost_price(product.getCostPrice())
                .selling_price(product.getSellingPrice())
                .agent_fee(product.getAgentFee())
                .super_agent_fee(product.getSuperAgentFee())
                .product_status(product.getProductStatus())
                .message("Product set to inactive successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(product -> ProductResponseDto.builder()
                        .product_id(product.getProductId())
                        .product_name(product.getProductName())
                        .cost_price(product.getCostPrice())
                        .selling_price(product.getSellingPrice())
                        .agent_fee(product.getAgentFee())
                        .super_agent_fee(product.getSuperAgentFee())
                        .product_status(product.getProductStatus())
                        .message("Success")
                        .build())
                .toList();
    }

    private void validatePricingAndFee(BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal agentFee, BigDecimal superAgentFee) {
        if (sellingPrice.compareTo(costPrice) <= 0) {
            throw new AppException(ProductErrorCode.PRD_0009);
        }

        BigDecimal totalFee = agentFee.add(superAgentFee);
        if (totalFee.compareTo(new BigDecimal("100.00")) > 0) {
            throw new AppException(ProductErrorCode.PRD_0010);
        }
    }
}
