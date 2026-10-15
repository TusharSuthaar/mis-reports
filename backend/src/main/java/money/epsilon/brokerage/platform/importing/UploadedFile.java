package money.epsilon.brokerage.platform.importing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * An uploaded Excel file, checked once the same way for every upload screen: not empty, an .xlsx, with its SHA-256
 * (used to refuse the exact same file twice).
 */
public record UploadedFile(String name, byte[] content, String sha256) {

    /**
     * @param defaultName used when the browser sends no name
     * @param what        for the message, e.g. "the Finesse client master" → "Upload the Finesse client master .xlsx file"
     */
    public static UploadedFile of(MultipartFile file, String defaultName, String what) {
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? defaultName : file.getOriginalFilename();
        if (file.isEmpty()) throw new IllegalArgumentException("The file is empty");
        if (!name.toLowerCase().endsWith(".xlsx")) throw new IllegalArgumentException("Upload " + what + " as an .xlsx file");
        try {
            byte[] content = file.getBytes();
            return new UploadedFile(name, content, sha256(content));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the upload", e);
        }
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
