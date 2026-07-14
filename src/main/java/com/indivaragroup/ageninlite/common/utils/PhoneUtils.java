package com.indivaragroup.ageninlite.common.utils;

public final class PhoneUtils {
    private PhoneUtils(){

    }

    public static String normalizeToE164(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Phone is required");
        }
        String digits = raw.replaceAll("[\\s\\-\\.\\(\\)]", "");
        if (!digits.matches("^\\+?\\d{6,15}$")) {
            throw new IllegalArgumentException("Phone must be 6-15 digits");
        }
        return toE164(digits);
    }

    public static String normalizePrefix(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Phone prefix is required");
        }
        String digits = raw.replaceAll("[\\s\\-\\.\\(\\)]", "");
        if (!digits.matches("^\\+?\\d{3,15}$")) {
            throw new IllegalArgumentException("Phone prefix must be 3-15 digits");
        }
        return toE164(digits);
    }

    private static String toE164(String digits) {
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("62")) {
            return "+" + digits;
        }
        if (digits.startsWith("0")) {
            return "+62" + digits.substring(1);
        }
        throw new IllegalArgumentException("Phone must start with +, 62, or 0");
    }
}
