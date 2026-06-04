package com.chatflow.notification.controller;

import com.chatflow.notification.dto.NotificationResponse;
import com.chatflow.notification.dto.UnreadCountResponse;
import com.chatflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> feed(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return notificationService.feed(callerId, cursor, Math.min(limit, MAX_LIMIT));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return new UnreadCountResponse(notificationService.unreadCount(callerId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        notificationService.markRead(callerId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        notificationService.markAllRead(callerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        notificationService.delete(callerId, id);
        return ResponseEntity.noContent().build();
    }
}
