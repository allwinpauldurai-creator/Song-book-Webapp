package com.example.songbook.file.validation;

import com.example.songbook.common.exception.ApiException;
import com.example.songbook.common.util.TokenUtils;
import com.example.songbook.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Server-side upload validation. Decisions are based on file content (magic bytes + parsing),
 * never on the client filename or browser-supplied content type.
 */
@Component
public class FileValidator {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes();
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final AppProperties.Storage limits;

    public FileValidator(AppProperties properties) {
        this.limits = properties.storage();
    }

    public ValidatedFile validatePdf(MultipartFile file) {
        Path temp = copyToTemp(file, limits.maxPdfBytes(), ".pdf");
        try {
            byte[] head = readHead(temp, 8);
            if (!startsWith(head, PDF_MAGIC)) {
                throw invalid("File is not a PDF document");
            }
            int pages;
            try (PDDocument document = Loader.loadPDF(temp.toFile())) {
                if (document.isEncrypted()) {
                    throw invalid("Encrypted PDFs are not supported");
                }
                pages = document.getNumberOfPages();
            } catch (IOException e) {
                throw invalid("PDF is malformed or unreadable");
            }
            if (pages < 1) {
                throw invalid("PDF has no pages");
            }
            return new ValidatedFile(temp, "application/pdf", "pdf", Files.size(temp), sha256(temp),
                    pages, sanitizeFilename(file.getOriginalFilename(), "document.pdf"));
        } catch (RuntimeException | IOException e) {
            deleteQuietly(temp);
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid("PDF could not be processed");
        }
    }

    public ValidatedFile validateImage(MultipartFile file) {
        Path temp = copyToTemp(file, limits.maxImageBytes(), ".img");
        try {
            byte[] head = readHead(temp, 12);
            String contentType;
            String extension;
            if (startsWith(head, PNG_MAGIC)) {
                contentType = "image/png";
                extension = "png";
            } else if (startsWith(head, JPEG_MAGIC)) {
                contentType = "image/jpeg";
                extension = "jpg";
            } else if (head.length >= 12 && startsWith(head, "RIFF".getBytes())
                    && Arrays.equals(Arrays.copyOfRange(head, 8, 12), "WEBP".getBytes())) {
                contentType = "image/webp";
                extension = "webp";
            } else {
                throw invalid("Cover must be a JPEG, PNG or WebP image");
            }
            if (!"image/webp".equals(contentType)) {
                checkDecodable(temp);
            }
            return new ValidatedFile(temp, contentType, extension, Files.size(temp), sha256(temp),
                    null, sanitizeFilename(file.getOriginalFilename(), "cover." + extension));
        } catch (RuntimeException | IOException e) {
            deleteQuietly(temp);
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid("Image could not be processed");
        }
    }

    /** Reads only the header to check dimensions, then confirms the image decodes. */
    private void checkDecodable(Path path) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) {
                throw invalid("Image is malformed or unreadable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw invalid("Image is malformed or unreadable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > limits.maxImageDimension() || h > limits.maxImageDimension()) {
                    throw invalid("Image dimensions must be at most " + limits.maxImageDimension() + " pixels");
                }
                reader.read(0);
            } catch (IOException e) {
                throw invalid("Image is malformed or unreadable");
            } finally {
                reader.dispose();
            }
        }
    }

    private Path copyToTemp(MultipartFile file, long maxBytes, String suffix) {
        if (file == null || file.isEmpty()) {
            throw invalid("File is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "File exceeds the maximum size of " + (maxBytes / (1024 * 1024)) + " MB");
        }
        try {
            Path temp = Files.createTempFile("songbook-upload-", suffix);
            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
                long copied = in.transferTo(out);
                if (copied > maxBytes) {
                    deleteQuietly(temp);
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File is too large");
                }
            }
            return temp;
        } catch (IOException e) {
            throw invalid("Upload could not be read");
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = TokenUtils.sha256();
        try (InputStream in = Files.newInputStream(path);
             DigestOutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            in.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] readHead(Path path, int n) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(n);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    /** Keeps only the base name and a safe character set; used for metadata only, never for storage keys. */
    static String sanitizeFilename(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.')) {
            return fallback;
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("INVALID_FILE", message);
    }
}
