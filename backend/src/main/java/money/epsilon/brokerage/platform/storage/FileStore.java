package money.epsilon.brokerage.platform.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps uploaded files on disk under mis.storage.dir. Files are stored under a random name; callers keep
 * the original file name themselves. Paths returned are relative to the store root.
 */
@Component
public class FileStore {

    private final Path root;

    public FileStore(@Value("${mis.storage.dir}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** @param area a fixed folder name such as "master-imports" */
    public String save(String area, byte[] content, String extension) {
        String relative = area + "/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
        return relative;
    }

    /**
     * A file kept in {@code area}, by the token {@link #save} returned. The token must belong to that area (a browser
     * cannot make the server read another area's files).
     */
    public byte[] read(String area, String token) {
        checkToken(area, token);
        return read(token);
    }

    /** @throws IllegalArgumentException unless {@code token} names a file in {@code area} */
    public void checkToken(String area, String token) {
        if (token == null || !token.startsWith(area + "/") || token.contains("..")) {
            throw new IllegalArgumentException("Unknown file token; upload the file again");
        }
    }

    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read stored file", e);
        }
    }

    private Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Path escapes the file store");
        return p;
    }
}
