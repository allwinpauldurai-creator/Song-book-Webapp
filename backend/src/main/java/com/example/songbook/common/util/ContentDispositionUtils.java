package com.example.songbook.common.util;

import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public final class ContentDispositionUtils {

    private ContentDispositionUtils() {
    }

    /** Turns a title into a safe file name base (ASCII letters, digits, dash, underscore, space). */
    public static String safeBaseName(String title) {
        String ascii = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{ASCII}]", "");
        String cleaned = ascii.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
        if (cleaned.isEmpty()) {
            cleaned = "songbook";
        }
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    /**
     * Builds the header with a plain quoted {@code filename}. Callers pass names from
     * {@link #safeBaseName}, which are pure ASCII, so no RFC 5987 {@code filename*} is needed.
     * (Passing a charset made Spring RFC 2047-encode even ASCII names, e.g.
     * {@code filename="=?UTF-8?Q?Book.pdf?="}, which non-browser clients save verbatim.)
     */
    public static ContentDisposition build(boolean attachment, String fileName) {
        ContentDisposition.Builder builder = attachment ? ContentDisposition.attachment() : ContentDisposition.inline();
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(fileName)) {
            return builder.filename(fileName, StandardCharsets.UTF_8).build();
        }
        return builder.filename(fileName).build();
    }
}
