package com.indivaragroup.ageninlite.service.commission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionCalculatorTest {

    private final CommissionCalculator calculator = new CommissionCalculator();

    @Test
    void calculateCommissionAmount_knownProfitAndPct_producesExpectedAmount() {
        BigDecimal result = calculator.calculateCommissionAmount(
                new BigDecimal("10000"), new BigDecimal("5"));
        assertEquals(0, new BigDecimal("500.00").compareTo(result));
    }

    @Test
    void calculateCommissionAmount_halfUpEdgeCase_producesCorrectRounding() {
        BigDecimal result = calculator.calculateCommissionAmount(
                new BigDecimal("333"), new BigDecimal("3"));
        assertEquals(0, new BigDecimal("9.99").compareTo(result));
    }

    @Test
    void calculateCommissionAmount_zeroProfit_returnsZero() {
        BigDecimal result = calculator.calculateCommissionAmount(
                BigDecimal.ZERO, new BigDecimal("10"));
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void calculateCommissionAmount_zeroFee_returnsZero() {
        BigDecimal result = calculator.calculateCommissionAmount(
                new BigDecimal("10000"), BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }
}
