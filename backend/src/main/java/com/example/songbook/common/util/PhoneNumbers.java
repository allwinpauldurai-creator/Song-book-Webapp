package com.example.songbook.common.util;

import com.example.songbook.common.exception.ApiException;

import java.util.regex.Pattern;

/**
 * Normalises mobile numbers to E.164 ({@code +<country><number>}), the only form stored and compared.
 * <ul>
 *   <li>Spaces, dashes, dots and brackets are ignored.</li>
 *   <li>A 10-digit Indian mobile (starting 6-9), optionally prefixed with 0 or 91, becomes {@code +91...}.</li>
 *   <li>Any other number must be written with its {@code +} country code.</li>
 * </ul>
 */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9][0-9]{9}$");

    private PhoneNumbers() {
    }

    /** @throws ApiException 400 {@code INVALID_MOBILE} when the input is not a usable mobile number */
    public static String normalize(String raw) {
        String result = tryNormalize(raw);
        if (result == null) {
            throw ApiException.badRequest("INVALID_MOBILE", "Enter a valid mobile number");
        }
        return result;
    }

    /** Like {@link #normalize} but returns {@code null} instead of throwing. */
    public static String tryNormalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (s.startsWith("00")) {
            s = "+" + s.substring(2);
        }
        if (!s.startsWith("+")) {
            if (s.startsWith("0") && s.length() == 11) {
                s = s.substring(1);
            } else if (s.startsWith("91") && s.length() == 12) {
                s = s.substring(2);
            }
            if (!INDIAN_MOBILE.matcher(s).matches()) {
                return null;
            }
            s = "+91" + s;
        }
        if (s.startsWith("+91") && !INDIAN_MOBILE.matcher(s.substring(3)).matches()) {
            return null;
        }
        return E164.matcher(s).matches() ? s : null;
    }

    /** For logs and audit details: keeps the country code and the last 3 digits. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 6) {
            return "***";
        }
        return e164.substring(0, 3) + "*".repeat(e164.length() - 6) + e164.substring(e164.length() - 3);
    }
}
