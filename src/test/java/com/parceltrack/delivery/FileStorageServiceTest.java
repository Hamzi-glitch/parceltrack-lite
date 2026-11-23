package com.parceltrack.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static final long TWO_MB = 2L * 1024 * 1024;

    @TempDir
    Path tempDir;

    private Path uploadDir;
    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        uploadDir = tempDir.resolve("uploads");
        storage = new FileStorageService(uploadDir.toString(), TWO_MB);
    }

    private static MockMultipartFile file(String name, String clientMime, byte[] content) {
        return new MockMultipartFile("photo", name, clientMime, content);
    }

    // ------------------------------------------------------------ accepted

    @Test
    void validPngIsStoredUnderRandomNameAndIsAReadableImage() throws IOException {
        String stored = storage.storeProofPhoto(file("door.png", "image/png", TestImages.png(40, 30)));

        assertThat(stored).matches("[0-9a-f-]{36}\\.png").doesNotContain("door");
        Path saved = uploadDir.resolve(stored);
        assertThat(saved).exists();
        assertThat(ImageIO.read(saved.toFile()).getWidth()).isEqualTo(40);
    }

    @Test
    void validJpegWithJpegExtensionIsStoredAsJpg() {
        String stored = storage.storeProofPhoto(file("PHOTO.JPEG", "image/jpeg", TestImages.jpg(20, 20)));
        assertThat(stored).endsWith(".jpg");
    }

    @Test
    void storedFileIsReEncodedSoAppendedPayloadIsRemoved() throws IOException {
        byte[] image = TestImages.png(10, 10);
        byte[] payload = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.US_ASCII);
        byte[] polyglot = Arrays.copyOf(image, image.length + payload.length);
        System.arraycopy(payload, 0, polyglot, image.length, payload.length);

        String stored = storage.storeProofPhoto(file("photo.png", "image/png", polyglot));

        String savedText = new String(Files.readAllBytes(uploadDir.resolve(stored)), StandardCharsets.ISO_8859_1);
        assertThat(savedText).doesNotContain("<?php");
    }

    // ------------------------------------------------------------ rejected (M3 test cases)

    @Test
    void shellPhpIsRejected() {
        assertRejected(file("shell.php", "application/x-php",
                "<?php echo 'pwned'; ?>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void shellPhpJpgDoubleExtensionIsRejectedEvenIfContentIsAnImage() {
        assertRejected(file("shell.php.jpg", "image/jpeg", TestImages.jpg(10, 10)));
    }

    @Test
    void renamedNonImageIsRejectedDespiteImageMimeType() {
        // Client claims image/jpeg; the server checks the real bytes.
        assertRejected(file("notes.jpg", "image/jpeg", "just some text, not an image".getBytes()));
    }

    @Test
    void pngContentWithJpgExtensionIsRejected() {
        assertRejected(file("photo.jpg", "image/jpeg", TestImages.png(10, 10)));
    }

    @Test
    void oversizedImageIsRejected() {
        FileStorageService small = new FileStorageService(uploadDir.toString(), 100);
        assertThatThrownBy(() -> small.storeProofPhoto(file("big.png", "image/png", TestImages.png(50, 50))))
                .isInstanceOf(DeliveryRuleException.class)
                .hasMessageContaining("MB or smaller");
    }

    @Test
    void corruptedPngIsRejected() {
        byte[] good = TestImages.png(50, 50);
        byte[] truncated = Arrays.copyOf(good, 40); // valid header, broken body
        assertRejected(file("broken.png", "image/png", truncated));
    }

    @Test
    void corruptedJpegIsRejected() {
        byte[] good = TestImages.jpg(64, 64);
        byte[] truncated = Arrays.copyOf(good, good.length / 2);
        assertRejected(file("broken.jpg", "image/jpeg", truncated));
    }

    @Test
    void emptyAndMissingFilesAreRejected() {
        assertRejected(file("empty.png", "image/png", new byte[0]));
        assertThatThrownBy(() -> storage.storeProofPhoto(null)).isInstanceOf(DeliveryRuleException.class);
    }

    @Test
    void badFilenamesAreRejected() {
        byte[] png = TestImages.png(5, 5);
        for (String name : new String[]{"noextension", ".png", "../../evil.png", "dir\\evil.png", "photo.gif",
                "photo.svg", "photo.png.exe", "photo."}) {
            assertRejected(file(name, "image/png", png));
        }
    }

    @Test
    void nothingIsLeftOnDiskAfterRejection() throws IOException {
        try {
            storage.storeProofPhoto(file("shell.php", "image/png", "x".getBytes()));
        } catch (DeliveryRuleException expected) {
            // expected
        }
        if (Files.exists(uploadDir)) {
            try (var files = Files.list(uploadDir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    // ------------------------------------------------------------ storage failure

    @Test
    void storageFailureRaisesFileStorageExceptionAndLeavesNoFile() throws IOException {
        Path notADirectory = tempDir.resolve("blocked");
        Files.writeString(notADirectory, "a file where the upload directory should be");
        FileStorageService broken = new FileStorageService(notADirectory.toString(), TWO_MB);

        assertThatThrownBy(() -> broken.storeProofPhoto(file("door.png", "image/png", TestImages.png(10, 10))))
                .isInstanceOf(FileStorageException.class);
        assertThat(Files.readString(notADirectory)).startsWith("a file");
    }

    // ------------------------------------------------------------ reading back

    @Test
    void resolveStoredRefusesPathTraversalAndUnknownNames() {
        assertThatThrownBy(() -> storage.resolveStored("../application.properties"))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> storage.resolveStored("00000000-0000-0000-0000-000000000000.png"))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void resolveStoredFindsAStoredFile() {
        String stored = storage.storeProofPhoto(file("door.png", "image/png", TestImages.png(10, 10)));
        assertThat(storage.resolveStored(stored)).exists();
        assertThat(storage.contentTypeOf(stored)).isEqualTo("image/png");
    }

    private void assertRejected(MockMultipartFile f) {
        assertThatThrownBy(() -> storage.storeProofPhoto(f))
                .as("upload %s should be rejected", f.getOriginalFilename())
                .isInstanceOf(DeliveryRuleException.class);
    }
}
