package com.sankalp.fairexit.service.vault;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Decides a file's type from its first bytes, not from its name or the client's word.
 * Only JPG, PNG and PDF are accepted (spec decision 27).
 */
public final class FileTypes {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String PDF = "application/pdf";
    public static final Set<String> ALLOWED = Set.of(JPEG, PNG, PDF);

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private FileTypes() {
    }

    public static Optional<String> sniff(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
