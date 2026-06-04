package com.chatflow.conversation.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque pagination cursor encoding (createdAt, id) of the last result, so the
 * next page resumes exactly where the previous ended. Base64 of "epochMillis:nanos:uuid".
 */
public record SearchCursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt.toEpochMilli() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static SearchCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(parts[0]))
                    .plusNanos(Long.parseLong(parts[1]) % 1_000_000);
            UUID id = UUID.fromString(parts[2]);
            return new SearchCursor(createdAt, id);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }
}
