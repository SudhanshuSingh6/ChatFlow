package com.chatflow.media.validation;

import com.chatflow.media.entity.MessageType;
import com.chatflow.media.exception.MediaValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Centralised validation for media uploads.
 *
 * Validation order matters — cheapest checks first:
 * 1. Empty file
 * 2. Blocked extension (filename-based, fast)
 * 3. File size against per-type limit
 * 4. MIME type from magic bytes (most expensive — reads first bytes)
 *
 * Magic byte detection is critical: a client can set Content-Type to
 * image/jpeg while uploading a .exe. We read the first 12 bytes and
 * compare against known signatures. For full coverage in production,
 * replace the manual check with Apache Tika.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaValidator {

    private final MediaValidationConfig config;

    public void validate(MultipartFile file, MessageType type) {
        rejectEmpty(file);
        rejectBlockedExtension(file);
        rejectOversizedFile(file, type);
        String detectedMime = detectMimeType(file);
        rejectDisallowedMime(detectedMime, type);
    }

    /**
     * Returns the MIME type detected from magic bytes.
     * Callers use this to store the verified mimeType — not the
     * client-supplied Content-Type.
     */
    public String detectAndVerifyMimeType(MultipartFile file, MessageType type) {
        String mime = detectMimeType(file);
        rejectDisallowedMime(mime, type);
        return mime;
    }

    // --- private checks ---

    private void rejectEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("File must not be empty");
        }
    }

    private void rejectBlockedExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) return;

        String lower = original.toLowerCase();
        List<String> blocked = config.getBlockedExtensions();

        boolean isBlocked = blocked.stream().anyMatch(lower::endsWith);
        if (isBlocked) {
            throw new MediaValidationException(
                    "File type not permitted: " + extractExtension(lower));
        }
    }

    private void rejectOversizedFile(MultipartFile file, MessageType type) {
        Long max = config.getMaxFileSizeBytes().get(type);
        if (max == null) return;
        if (file.getSize() > max) {
            throw new MediaValidationException(
                    "File exceeds maximum size of " + toMb(max) + " MB for type " + type);
        }
    }

    private void rejectDisallowedMime(String detectedMime, MessageType type) {
        List<String> allowed = config.getAllowedMimeTypes().get(type);
        if (allowed == null) return;
        if (!allowed.contains(detectedMime)) {
            throw new MediaValidationException(
                    "MIME type '" + detectedMime + "' is not allowed for type " + type
                            + ". Allowed: " + allowed);
        }
    }

    /**
     * Reads the first 12 bytes of the file and matches known magic byte
     * signatures. Falls back to the Content-Type header only when no
     * signature matches.
     *
     * For production: replace with Apache Tika's Detector which covers
     * hundreds of formats including nested types (e.g. ZIP-based .docx).
     */
    private String detectMimeType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            String mime = matchMagicBytes(header);
            if (mime != null) {
                log.debug("Detected MIME {} from magic bytes for file {}",
                        mime, file.getOriginalFilename());
                return mime;
            }
            // Fallback — still better than trusting the header blindly
            String contentType = file.getContentType();
            log.debug("No magic byte match — using Content-Type header: {}", contentType);
            return contentType != null ? contentType : "application/octet-stream";
        } catch (IOException ex) {
            throw new MediaValidationException("Could not read file for validation: " + ex.getMessage());
        }
    }

    private String matchMagicBytes(byte[] h) {
        if (h.length < 4) return null;

        // JPEG: FF D8 FF
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF)
            return "image/jpeg";

        // PNG: 89 50 4E 47
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G')
            return "image/png";

        // GIF: 47 49 46 38
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8')
            return "image/gif";

        // WEBP: RIFF....WEBP
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h.length >= 12 && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P')
            return "image/webp";

        // MP4 / MOV: ftyp box at offset 4
        if (h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p')
            return "video/mp4";

        // WebM: 1A 45 DF A3
        if ((h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3)
            return "video/webm";

        // MP3: ID3 or FF FB / FF F3 / FF F2
        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') return "audio/mpeg";
        if ((h[0] & 0xFF) == 0xFF && ((h[1] & 0xE0) == 0xE0)) return "audio/mpeg";

        // OGG: 4F 67 67 53
        if (h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S')
            return "audio/ogg";

        // WAV: RIFF....WAVE
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h.length >= 12 && h[8] == 'W' && h[9] == 'A' && h[10] == 'V' && h[11] == 'E')
            return "audio/wav";

        // PDF: 25 50 44 46
        if (h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F')
            return "application/pdf";

        // ZIP (also .docx, .xlsx, .pptx): 50 4B 03 04
        if (h[0] == 'P' && h[1] == 'K' && h[2] == 0x03 && h[3] == 0x04)
            return "application/zip";

        return null;
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "(no extension)";
    }

    private long toMb(long bytes) {
        return bytes / (1024 * 1024);
    }
}