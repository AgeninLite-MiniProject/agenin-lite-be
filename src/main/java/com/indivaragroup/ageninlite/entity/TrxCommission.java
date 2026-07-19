package com.indivaragroup.ageninlite.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trx_commissions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_commissions_idempotency",
                columnNames = {"item_id", "beneficiary_id", "commission_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrxCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "commission_id")
    private UUID commissionId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "source_user_id", nullable = false)
    private UUID sourceUserId;

    @Column(name = "commission_type", nullable = false, length = 20)
    private String commissionType;

    @Column(name = "fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
