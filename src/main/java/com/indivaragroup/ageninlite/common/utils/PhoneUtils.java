package com.indivaragroup.ageninlite.common.utils;

import java.util.regex.Pattern;

public final class PhoneUtils {

    private static final String COUNTRY_CODE = "62";
    private static final String E164_PREFIX = "+" + COUNTRY_CODE;

    /**
     * Nomor seluler Indonesia setelah country code:
     * - dimulai dengan 8
     * - total 9 sampai 12 digit
     *
     * Contoh: 81234567890
     */
    private static final Pattern INDONESIAN_MOBILE_NUMBER = Pattern.compile("^8\\d{8,11}$");

    /**
     * Prefix pencarian:
     * - minimal 3 digit
     * - harus dimulai dengan 8
     */
    private static final Pattern INDONESIAN_MOBILE_PREFIX = Pattern.compile("^8\\d{2,11}$");

    private PhoneUtils(){

    }

    public static String normalizeToE164(String rawPhone) {
        String compactPhone = sanitize(rawPhone);
        String nationalNumber = extractNationalNumber(compactPhone);

        if (!INDONESIAN_MOBILE_NUMBER.matcher(nationalNumber).matches()) {
            throw new IllegalArgumentException("Indonesian mobile number must start with 8 and contain 9-12 digits");
        }

        return E164_PREFIX + nationalNumber;
    }

    public static String normalizePrefix(String rawPrefix) {
        String compactPrefix = sanitize(rawPrefix);
        String nationalPrefix = extractNationalNumber(compactPrefix);

        if (!INDONESIAN_MOBILE_PREFIX.matcher(nationalPrefix).matches()) {
            throw new IllegalArgumentException("Phone prefix must start with 8 and contain at least 3 digits");
        }
        return E164_PREFIX + nationalPrefix;
    }

    private static String sanitize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        String compactPhone = rawPhone.trim().replaceAll("[\\s\\-().]", "");

        if (!compactPhone.matches("^\\+?\\d+$")) {
            throw new IllegalArgumentException(
                    "Phone number contains unsupported characters"
            );
        }

        return compactPhone;
    }

    private static String extractNationalNumber(String compactPhone) {
        if (compactPhone.startsWith(E164_PREFIX)) {
            return compactPhone.substring(E164_PREFIX.length());
        }
        if (compactPhone.startsWith(COUNTRY_CODE)) {
            return compactPhone.substring(COUNTRY_CODE.length());
        }
        if (compactPhone.startsWith("0")) {
            return compactPhone.substring(1);
        }
        if (compactPhone.startsWith("8")) {
            return compactPhone;
        }

        throw new IllegalArgumentException("Phone number must use Indonesian country code");
    }
}
