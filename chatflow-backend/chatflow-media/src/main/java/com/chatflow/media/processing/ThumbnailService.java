package com.chatflow.media.processing;

import com.chatflow.media.storage.MediaKeys;
import com.chatflow.media.storage.MediaStorageService;
import com.chatflow.media.storage.StoredMedia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Generates a thumbnail for a stored media object: reads the original from the shared store,
 * resizes (Thumbnailator for images, an ffmpeg frame-grab for video), writes the thumbnail
 * back, and returns its URL. Audio/file have none.
 *
 * <p>Never throws — on any failure it returns {@link Optional#empty()} and the caller simply
 * doesn't announce a thumbnail (the client shows a placeholder), matching the old in-process
 * pipeline's fail-soft behavior.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final MediaStorageService storage;

    @Value("${app.media.thumbnail.max-size:300}")
    private int maxSize;

    @Value("${app.media.thumbnail.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.media.thumbnail.ffmpeg-timeout-seconds:20}")
    private long ffmpegTimeoutSeconds;

    /** @return the stored thumbnail's URL, or empty if none was produced. */
    public Optional<String> generate(String storageKey, String messageType, String mimeType) {
        try {
            byte[] original = storage.read(storageKey);
            byte[] thumbnail = switch (messageType == null ? "" : messageType) {
                case "IMAGE" -> generateImageThumbnail(original);
                case "VIDEO" -> generateVideoThumbnail(original, mimeType);
                default -> null; // AUDIO, FILE — no visual thumbnail
            };
            if (thumbnail == null || thumbnail.length == 0) {
                return Optional.empty();
            }
            StoredMedia stored = storage.storeBytes(
                    thumbnail, MediaKeys.thumbnailKey(storageKey), "image/jpeg");
            return Optional.of(stored.getPublicUrl());
        } catch (Exception ex) {
            log.warn("Thumbnail generation failed for storageKey={} type={}: {}",
                    storageKey, messageType, ex.getMessage());
            return Optional.empty();
        }
    }

    private byte[] generateImageThumbnail(byte[] source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(source))
                .size(maxSize, maxSize)
                .outputFormat("jpg")
                .toOutputStream(out);
        return out.toByteArray();
    }

    /** Extracts a single frame ~1s in via ffmpeg. Returns null on any failure. */
    private byte[] generateVideoThumbnail(byte[] source, String mimeType) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("media-src-", videoExtension(mimeType));
            output = Files.createTempFile("media-thumb-", ".jpg");
            Files.write(input, source);

            Process process = new ProcessBuilder(
                    ffmpegPath, "-y", "-ss", "00:00:01", "-i", input.toString(),
                    "-vframes", "1", "-vf", "scale=" + maxSize + ":-1", "-f", "image2",
                    output.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(ffmpegTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg timed out after {}s", ffmpegTimeoutSeconds);
                return null;
            }
            if (process.exitValue() != 0 || !Files.exists(output) || Files.size(output) == 0) {
                log.warn("ffmpeg produced no frame (exit={})", process.exitValue());
                return null;
            }
            return generateImageThumbnail(Files.readAllBytes(output));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Video thumbnail extraction failed: {}", ex.getMessage());
            return null;
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    private String videoExtension(String mimeType) {
        return switch (mimeType == null ? "" : mimeType) {
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            case "video/x-msvideo" -> ".avi";
            default -> ".tmp";
        };
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Could not delete temp file {}: {}", path, ex.getMessage());
        }
    }
}
