package com.smartbank.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyUtilTests {

    @Test
    @DisplayName("Format standard positive amounts with Indian grouping (Lakhs and Crores)")
    void testFormatPositiveAmounts() {
        assertEquals("₹10,000.00", CurrencyUtil.format(new BigDecimal("10000.00")));
        assertEquals("₹50,000.00", CurrencyUtil.format(new BigDecimal("50000.00")));
        assertEquals("₹3,36,000.00", CurrencyUtil.format(new BigDecimal("336000.00")));
        assertEquals("₹1,00,00,000.00", CurrencyUtil.format(new BigDecimal("10000000.00")));
        assertEquals("₹500.00", CurrencyUtil.format(new BigDecimal("500.00")));
        assertEquals("₹12.50", CurrencyUtil.format(new BigDecimal("12.50")));
    }

    @Test
    @DisplayName("Format zero and null safely")
    void testFormatZeroAndNull() {
        assertEquals("₹0.00", CurrencyUtil.format(BigDecimal.ZERO));
        assertEquals("₹0.00", CurrencyUtil.format((BigDecimal) null));
        assertEquals("₹0.00", CurrencyUtil.format((Number) null));
    }

    @Test
    @DisplayName("Format negative amounts correctly with minus sign before currency symbol")
    void testFormatNegativeAmounts() {
        assertEquals("-₹500.00", CurrencyUtil.format(new BigDecimal("-500.00")));
        assertEquals("-₹10,000.00", CurrencyUtil.format(new BigDecimal("-10000.00")));
        assertEquals("-₹3,36,000.00", CurrencyUtil.format(new BigDecimal("-336000.00")));
    }

    @Test
    @DisplayName("Format without symbol produces correctly grouped strings")
    void testFormatWithoutSymbol() {
        assertEquals("10,000.00", CurrencyUtil.formatWithoutSymbol(new BigDecimal("10000.00")));
        assertEquals("3,36,000.00", CurrencyUtil.formatWithoutSymbol(new BigDecimal("336000.00")));
        assertEquals("-500.00", CurrencyUtil.formatWithoutSymbol(new BigDecimal("-500.00")));
        assertEquals("0.00", CurrencyUtil.formatWithoutSymbol(BigDecimal.ZERO));
        assertEquals("0.00", CurrencyUtil.formatWithoutSymbol((BigDecimal) null));
    }

    @Test
    @DisplayName("Format with sign for credit and debit presentation")
    void testFormatWithSign() {
        assertEquals("+₹10,000.00", CurrencyUtil.formatWithSign(new BigDecimal("10000.00"), true));
        assertEquals("-₹5,000.00", CurrencyUtil.formatWithSign(new BigDecimal("5000.00"), false));
        assertEquals("+₹0.00", CurrencyUtil.formatWithSign(BigDecimal.ZERO, true));
        assertEquals("-₹0.00", CurrencyUtil.formatWithSign(BigDecimal.ZERO, false));
    }

    @Test
    @DisplayName("Format generic Number types (Integer, Double, Long)")
    void testFormatGenericNumbers() {
        assertEquals("₹10,000.00", CurrencyUtil.format(10000));
        assertEquals("₹50,000.50", CurrencyUtil.format(50000.50));
        assertEquals("₹3,36,000.00", CurrencyUtil.format(336000L));
    }

    @Test
    @DisplayName("Verify currency metadata is INR and ₹")
    void testCurrencyConstants() {
        assertEquals("INR", CurrencyUtil.CURRENCY_CODE);
        assertEquals("₹", CurrencyUtil.CURRENCY_SYMBOL);
        CurrencyUtil util = new CurrencyUtil();
        assertEquals("INR", util.getCurrencyCode());
        assertEquals("₹", util.getCurrencySymbol());
    }
}
