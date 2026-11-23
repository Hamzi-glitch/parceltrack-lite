package com.parceltrack.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Stores proof-of-delivery photos securely:
 * <ul>
 *   <li>JPG and PNG only, max size enforced on the server;</li>
 *   <li>exactly one extension in the filename (so "shell.php.jpg" is rejected);</li>
 *   <li>the file's real bytes must be a JPG/PNG matching its extension (client MIME type is ignored);</li>
 *   <li>the image is fully decoded (corrupted files are rejected) and then RE-ENCODED, so any
 *       script or payload hidden inside the original file is thrown away;</li>
 *   <li>saved under a random UUID filename in a directory outside the web root;</li>
 *   <li>never served directly: only through an authorized controller with an image content type.</li>
 * </ul>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    static final int MAX_DIMENSION = 8000;
    private static final Pattern STORED_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    enum ImageType {
        JPG("jpg", "jpeg", "image/jpeg", JPG_MAGIC),
        PNG("png", "png", "image/png", PNG_MAGIC);

        final String extension;
        final String formatName;
        final String contentType;
        private final byte[] magic;

        ImageType(String extension, String formatName, String contentType, byte[] magic) {
            this.extension = extension;
            this.formatName = formatName;
            this.contentType = contentType;
            this.magic = magic;
        }

        boolean matches(byte[] bytes) {
            return bytes.length >= magic.length
                    && Arrays.equals(Arrays.copyOf(bytes, magic.length), magic);
        }
    }

    private final Path uploadDir;
    private final long maxBytes;

    public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir,
                              @Value("${app.upload.max-bytes:2097152}") long maxBytes) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    /**
     * Validates and stores a photo. Returns the random stored filename.
     *
     * @throws DeliveryRuleException if the file is not an acceptable image (user's fault)
     * @throws FileStorageException  if the server could not save it (server's fault)
     */
    public String storeProofPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DeliveryRuleException("Please choose a JPG or PNG photo to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new DeliveryRuleException(tooLargeMessage());
        }
        ImageType type = typeFromFilename(file.getOriginalFilename());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileStorageException("Could not read uploaded file", e);
        }
        if (bytes.length > maxBytes) {
            throw new DeliveryRuleException(tooLargeMessage());
        }
        if (!type.matches(bytes)) {
            throw new DeliveryRuleException("The file is not a valid JPG or PNG image.");
        }
        BufferedImage image = decode(bytes, type);
        return write(image, type);
    }

    /** Resolves a stored filename to its path, refusing anything that is not one of our names. */
    public Path resolveStored(String storedFilename) {
        if (storedFilename == null || !STORED_NAME.matcher(storedFilename).matches()) {
            throw new ParcelNotFoundException();
        }
        Path path = uploadDir.resolve(storedFilename).normalize();
        if (!path.startsWith(uploadDir) || !Files.isRegularFile(path)) {
            throw new ParcelNotFoundException();
        }
        return path;
    }

    public String contentTypeOf(String storedFilename) {
        return storedFilename.endsWith(".png") ? ImageType.PNG.contentType : ImageType.JPG.contentType;
    }

    /** Best-effort cleanup, e.g. when the database insert after a successful save fails. */
    public void deleteQuietly(String storedFilename) {
        try {
            Path path = uploadDir.resolve(storedFilename).normalize();
            if (path.startsWith(uploadDir)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete orphaned upload {}", storedFilename);
        }
    }

    Path getUploadDir() {
        return uploadDir;
    }

    static ImageType typeFromFilename(String originalFilename) {
        String invalid = "Only JPG and PNG photos are accepted.";
        if (originalFilename == null || originalFilename.isBlank()
                || originalFilename.contains("/") || originalFilename.contains("\\")
                || originalFilename.indexOf('\0') >= 0) {
            throw new DeliveryRuleException(invalid);
        }
        String name = originalFilename.toLowerCase(Locale.ROOT);
        int firstDot = name.indexOf('.');
        int lastDot = name.lastIndexOf('.');
        if (firstDot <= 0 || firstDot != lastDot) {
            // No extension, hidden file (".jpg"), or double extension ("shell.php.jpg").
            throw new DeliveryRuleException(invalid);
        }
        return switch (name.substring(lastDot + 1)) {
            case "jpg", "jpeg" -> ImageType.JPG;
            case "png" -> ImageType.PNG;
            default -> throw new DeliveryRuleException(invalid);
        };
    }

    private BufferedImage decode(byte[] bytes, ImageType type) {
        String corrupted = "The image is corrupted or could not be read.";
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(type.formatName);
        if (!readers.hasNext()) {
            throw new IllegalStateException("No image reader for " + type.formatName);
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            AtomicBoolean warned = new AtomicBoolean(false);
            reader.addIIOReadWarningListener((source, warning) -> warned.set(true));
            reader.setInput(in, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new DeliveryRuleException("The image dimensions are not allowed (max "
                        + MAX_DIMENSION + " x " + MAX_DIMENSION + " pixels).");
            }
            BufferedImage image = reader.read(0);
            if (image == null || warned.get()) {
                // e.g. a truncated JPEG, which Java decodes with only a warning.
                throw new DeliveryRuleException(corrupted);
            }
            return image;
        } catch (DeliveryRuleException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new DeliveryRuleException(corrupted);
        } finally {
            reader.dispose();
        }
    }

    private String write(BufferedImage image, ImageType type) {
        BufferedImage toWrite = type == ImageType.JPG ? withoutAlpha(image) : image;
        String storedFilename = UUID.randomUUID() + "." + type.extension;
        Path target = uploadDir.resolve(storedFilename).normalize();
        Path temp = null;
        try {
            Files.createDirectories(uploadDir);
            temp = Files.createTempFile(uploadDir, "upload-", ".tmp");
            if (!ImageIO.write(toWrite, type.formatName, temp.toFile())) {
                throw new IOException("No image writer for " + type.formatName);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            return storedFilename;
        } catch (IOException | RuntimeException e) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // nothing more we can do
                }
            }
            throw new FileStorageException("Could not store uploaded photo", e);
        }
    }

    /** JPEG cannot hold transparency; flatten onto white so ImageIO can write it. */
    private static BufferedImage withoutAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha() && image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private String tooLargeMessage() {
        return "The photo must be " + (maxBytes / (1024 * 1024)) + " MB or smaller.";
    }
}
