package com.indivaragroup.ageninlite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trx_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrxTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "trx_id")
    private UUID trxId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_profit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalProfit;

    @Builder.Default
    @Column(name = "trx_status", nullable = false, length = 20)
    private String trxStatus = "PENDING";

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
