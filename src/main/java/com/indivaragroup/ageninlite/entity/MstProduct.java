package com.indivaragroup.ageninlite.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mst_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MstProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "cost_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "agent_fee", nullable = false, precision = 5, scale = 2)
    private BigDecimal agentFee;

    @Column(name = "super_agent_fee", nullable = false, precision = 5, scale = 2)
    private BigDecimal superAgentFee;

    @Builder.Default
    @Column(name = "product_status", nullable = false, length = 20)
    private String productStatus = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
