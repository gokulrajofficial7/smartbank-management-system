package com.smartbank.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Shared central utility for Indian Rupee (INR / ₹) currency formatting.
 * Implements locale-aware Indian numbering system formatting (Lakhs and Crores grouping):
 * - ₹10,000.00
 * - ₹50,000.00
 * - ₹3,36,000.00
 * - ₹1,00,00,000.00
 * - -₹500.00
 * - ₹0.00
 */
@Component("currencyUtil")
public class CurrencyUtil {

    public static final Locale INDIA_LOCALE = Locale.forLanguageTag("en-IN");
    public static final String CURRENCY_CODE = "INR";
    public static final String CURRENCY_SYMBOL = "₹";

    /**
     * Standard NumberFormat for INR locale.
     */
    public static NumberFormat getIndianCurrencyInstance() {
        NumberFormat nf = NumberFormat.getCurrencyInstance(INDIA_LOCALE);
        nf.setCurrency(Currency.getInstance(CURRENCY_CODE));
        return nf;
    }

    /**
     * Formats monetary value into Indian Rupee representation with Indian numbering grouping (₹#,##,##0.00).
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return CURRENCY_SYMBOL + "0.00";
        }
        boolean isNegative = amount.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal absAmount = amount.abs().setScale(2, RoundingMode.HALF_UP);
        String formatted = formatIndianNumber(absAmount);
        return (isNegative ? "-" + CURRENCY_SYMBOL : CURRENCY_SYMBOL) + formatted;
    }

    public static String format(Number amount) {
        if (amount == null) {
            return CURRENCY_SYMBOL + "0.00";
        }
        if (amount instanceof BigDecimal bd) {
            return format(bd);
        }
        return format(BigDecimal.valueOf(amount.doubleValue()));
    }

    public static String formatWithoutSymbol(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        boolean isNegative = amount.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal absAmount = amount.abs().setScale(2, RoundingMode.HALF_UP);
        String formatted = formatIndianNumber(absAmount);
        return (isNegative ? "-" : "") + formatted;
    }

    public static String formatWithoutSymbol(Number amount) {
        if (amount == null) {
            return "0.00";
        }
        if (amount instanceof BigDecimal bd) {
            return formatWithoutSymbol(bd);
        }
        return formatWithoutSymbol(BigDecimal.valueOf(amount.doubleValue()));
    }

    public static String formatWithSign(BigDecimal amount, boolean isCredit) {
        if (amount == null) {
            return CURRENCY_SYMBOL + "0.00";
        }
        String prefix = isCredit ? "+" + CURRENCY_SYMBOL : "-" + CURRENCY_SYMBOL;
        BigDecimal absAmount = amount.abs().setScale(2, RoundingMode.HALF_UP);
        return prefix + formatIndianNumber(absAmount);
    }

    public static String formatWithSign(Number amount, boolean isCredit) {
        if (amount == null) {
            return CURRENCY_SYMBOL + "0.00";
        }
        if (amount instanceof BigDecimal bd) {
            return formatWithSign(bd, isCredit);
        }
        return formatWithSign(BigDecimal.valueOf(amount.doubleValue()), isCredit);
    }

    /**
     * Formats using the Indian Numbering System (Lakhs and Crores):
     * The last 3 digits are grouped, then subsequent groups are 2 digits each.
     */
    public static String formatIndianNumber(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        String plain = scaled.toPlainString();
        int dotIdx = plain.indexOf('.');
        String intPart = dotIdx >= 0 ? plain.substring(0, dotIdx) : plain;
        String decPart = dotIdx >= 0 ? plain.substring(dotIdx) : ".00";

        if (intPart.length() <= 3) {
            return intPart + decPart;
        }

        String lastThree = intPart.substring(intPart.length() - 3);
        String remaining = intPart.substring(0, intPart.length() - 3);

        StringBuilder restSb = new StringBuilder();
        int count = 0;
        for (int i = remaining.length() - 1; i >= 0; i--) {
            if (count == 2) {
                restSb.insert(0, ',');
                count = 0;
            }
            restSb.insert(0, remaining.charAt(i));
            count++;
        }

        return restSb.append(',').append(lastThree).append(decPart).toString();
    }

    public String getCurrencyCode() {
        return CURRENCY_CODE;
    }

    public String getCurrencySymbol() {
        return CURRENCY_SYMBOL;
    }
}
