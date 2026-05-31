package com.chatflow.media.processing;

import com.chatflow.media.entity.MessageType;
import com.chatflow.media.storage.MediaKeys;
import com.chatflow.media.storage.MediaStorageService;
import com.chatflow.media.storage.StoredMedia;
import net.coobird.thumbnailator.Thumbnails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Phase 6 — asynchronous thumbnail generation.
 *
 * <p>Listens for {@link MediaProcessingEvent} off the request thread, generates
 * a thumbnail (Thumbnailator for images, FFmpeg for videos), stores it via the
 * {@link MediaStorageService} abstraction, then publishes
 * {@link ThumbnailGeneratedEvent}. Audio and generic files have no thumbnail.
 *
 * <p>Failures never propagate: on any error the thumbnail simply stays null and
 * the client shows a placeholder. The upload response is never blocked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final MediaStorageService mediaStorageService;
    private final ApplicationEventPublisher eventPublisher;

    /** Max thumbnail edge in pixels — aspect ratio preserved. */
    @Value("${app.media.thumbnail.max-size:300}")
    private int maxSize;

    /** Path/name of the ffmpeg binary; override if not on PATH. */
    @Value("${app.media.thumbnail.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** Seconds to wait for ffmpeg before giving up. */
    @Value("${app.media.thumbnail.ffmpeg-timeout-seconds:20}")
    private long ffmpegTimeoutSeconds;

    @Async("mediaProcessingExecutor")
    @EventListener
    public void onMediaProcessing(MediaProcessingEvent event) {
        try {
            byte[] thumbnail = switch (event.getMessageType()) {
                case IMAGE -> generateImageThumbnail(event.getFileBytes());
                case VIDEO -> generateVideoThumbnail(event.getFileBytes(), event.getMimeType());
                case AUDIO, FILE -> null; // no visual thumbnail
            };

            if (thumbnail == null || thumbnail.length == 0) {
                log.debug("No thumbnail generated for mediaId={} type={}",
                        event.getMediaMessageId(), event.getMessageType());
                return;
            }

            String thumbKey = MediaKeys.thumbnailKey(event.getStorageKey());
            StoredMedia stored = mediaStorageService.storeBytes(
                    thumbnail, thumbKey, "image/jpeg");

            log.debug("Generated thumbnail mediaId={} key={} size={} bytes",
                    event.getMediaMessageId(), thumbKey, thumbnail.length);

            eventPublisher.publishEvent(new ThumbnailGeneratedEvent(
                    event.getMediaMessageId(), stored.getPublicUrl()));

        } catch (Exception ex) {
            // Fallback: leave thumbnail null, never fail the pipeline
            log.warn("Thumbnail generation failed for mediaId={} type={}: {}",
                    event.getMediaMessageId(), event.getMessageType(), ex.getMessage());
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

    /**
     * Extracts a single frame ~1s in via FFmpeg. Returns null (placeholder
     * fallback) if ffmpeg is unavailable or fails — never throws.
     */
    private byte[] generateVideoThumbnail(byte[] source, String mimeType) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("media-src-", videoExtension(mimeType));
            output = Files.createTempFile("media-thumb-", ".jpg");
            Files.write(input, source);

            Process process = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-ss", "00:00:01",
                    "-i", input.toString(),
                    "-vframes", "1",
                    "-vf", "scale=" + maxSize + ":-1",
                    "-f", "image2",
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
            // Re-encode through Thumbnailator to normalise dimensions/format
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
        return switch (mimeType) {
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
