package com.smartbank.util;

import org.springframework.stereotype.Component;

/**
 * Shared utility for masking sensitive customer identifiers such as account numbers.
 */
@Component("maskUtil")
public class MaskUtil {

    public static String maskAccountNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "••••";
        }
        String clean = raw.trim();
        if (clean.length() <= 4) {
            return "••••" + clean;
        }
        String last4 = clean.substring(clean.length() - 4);
        if (clean.startsWith("SB")) {
            return "SB••••••" + last4;
        }
        return "•••• •••• " + last4;
    }
}
