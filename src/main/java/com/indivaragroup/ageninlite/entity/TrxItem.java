package com.indivaragroup.ageninlite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "trx_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_trx_items_product", columnNames = {"trx_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "trx_id", nullable = false)
    private UUID trxId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "item_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal itemAmount;

    @Column(name = "profit", nullable = false, precision = 19, scale = 2)
    private BigDecimal profit;
}
