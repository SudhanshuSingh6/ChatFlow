package com.chatflow.notification.dto;

import com.chatflow.notification.entity.Notification;
import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID actorId,
        NotificationType type,
        ReferenceType referenceType,
        UUID referenceId,
        String preview,
        int eventCount,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getActorId(),
                n.getType(),
                n.getReferenceType(),
                n.getReferenceId(),
                n.getPreview(),
                n.getEventCount(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
