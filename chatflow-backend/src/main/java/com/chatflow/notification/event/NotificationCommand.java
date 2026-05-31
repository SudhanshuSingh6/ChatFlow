package com.chatflow.notification.event;

import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;

import java.util.List;
import java.util.UUID;

public record NotificationCommand(
        List<UUID> recipientIds,
        UUID actorId,
        NotificationType type,
        ReferenceType referenceType,
        UUID referenceId,
        String preview,
        boolean coalesce
) {
}
