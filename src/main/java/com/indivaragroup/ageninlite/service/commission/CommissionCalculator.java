package com.indivaragroup.ageninlite.service.commission;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CommissionCalculator {

    public BigDecimal calculateCommissionAmount(BigDecimal profit, BigDecimal feePercentage) {
        return profit
                .multiply(feePercentage)
                .setScale(2, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
