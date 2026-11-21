package com.moviehub.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.moviehub.common.exception.BadRequestException;

/**
 * Stores avatar images on the local disk under {@code <app.storage.dir>/avatars}.
 * Images are identified by their magic bytes, never by the client-provided content type or file name.
 */
@Component
public class AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);

    /** Public URL prefix served by FileController. */
    public static final String URL_PREFIX = "/api/files/avatars/";
    public static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern FILE_NAME = Pattern.compile("^[0-9]+-[0-9a-f\\-]{36}\\.(png|jpg|webp)$");

    public enum ImageType {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp");

        final String extension;
        public final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    private final Path directory;

    public AvatarStorage(StorageProperties props) {
        this.directory = Path.of(props.dir(), "avatars").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create avatar directory " + directory, e);
        }
    }

    /** @return the public URL of the stored image */
    public String store(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose an image");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Image must be 2 MB or smaller");
        }
        byte[] header = readHeader(file);
        ImageType type = detect(header)
                .orElseThrow(() -> new BadRequestException("Only PNG, JPEG or WebP images are allowed"));

        String fileName = userId + "-" + UUID.randomUUID() + "." + type.extension;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save avatar", e);
        }
        return URL_PREFIX + fileName;
    }

    /** Deletes a previously stored avatar (ignores URLs that are not ours). */
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        resolve(url.substring(URL_PREFIX.length())).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Could not delete avatar {}", path, e);
            }
        });
    }

    /** Safe lookup for serving: only names we generated, only inside the avatar directory. */
    public Optional<Path> resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path path = directory.resolve(fileName).normalize();
        return path.startsWith(directory) && Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public static Optional<ImageType> detect(byte[] h) {
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of(ImageType.PNG);
        }
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return Optional.of(ImageType.JPEG);
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the image");
        }
    }
}
