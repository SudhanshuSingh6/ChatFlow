# Notification Feature — v1 Implementation

**Status:** design / ready to build
**Scope:** in-app notifications only — persistent feed **+** live WebSocket push.
**Explicitly out of scope:** push notifications (FCM/APNS), email, SMS.

---

## 1. Overview

A persistent `notifications` table is the source of truth. Every triggering event:

1. **persists** a notification row for each recipient, and
2. **pushes** it live over the existing `WebSocketGateway` if the recipient is connected.

Offline users see the notification when they next call the feed API. This reuses
existing project patterns:

- `ddl-auto: update` — Hibernate creates the table from the entity (no Flyway).
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` — same style as
  `ThumbnailEventListener`; guarantees no notification is sent for rolled-back data.
- `WebSocketGateway.sendToUser(s)` + `CrossServerRelay` — multi-server fan-out for free.
- `Principal.getName()` → `UUID` — controller auth, identical to every other controller.

### v1 decisions (locked)

| Decision | Choice |
|----------|--------|
| Delivery | Persist always + live push if connected |
| Architecture | **Event-driven** — services publish `NotificationEvent`; one listener persists + pushes |
| Message flooding | **Coalescing ON** — collapse unread message notifications per conversation/group |
| Active-conversation suppression | **Deferred to v2** |

### Triggers (v1)

| Source | Method | Recipient(s) | Type |
|--------|--------|--------------|------|
| `FriendService.sendRequest` | friend request sent | request recipient | `FRIEND_REQUEST` |
| `FriendService.acceptRequest` | request accepted | original requester | `FRIEND_REQUEST_ACCEPTED` |
| `GroupService.addMember` | member(s) added | added users | `GROUP_MEMBER_ADDED` |
| `GroupService.removeMember` | member removed | removed user | `GROUP_MEMBER_REMOVED` |
| `GroupService.updateMemberRole` | role changed | target user | `GROUP_ROLE_CHANGED` |
| `GroupService.transferOwnership` | ownership transferred | new owner | `GROUP_OWNERSHIP_TRANSFERRED` |
| `ChatService.sendMessage` | 1:1 message | receiver | `NEW_MESSAGE` (coalesced) |
| `GroupChatService` send | group message | all members except sender | `NEW_GROUP_MESSAGE` (coalesced) |

---

## 2. File plan

All new code under `com.chatflow.notification`.

```
notification/
├── entity/
│   ├── Notification.java
│   ├── NotificationType.java
│   └── ReferenceType.java
├── repository/
│   └── NotificationRepository.java
├── dto/
│   ├── NotificationResponse.java
│   └── UnreadCountResponse.java
├── event/
│   └── NotificationEvent.java
├── listener/
│   └── NotificationEventListener.java
├── service/
│   └── NotificationService.java
└── controller/
    └── NotificationController.java
```

**Changed files**

- `infra/websocket/OutboundMessage.java` — add `NOTIFICATION`, `NOTIFICATION_READ` to `Type`.
- `config/AsyncConfig.java` — add `notificationExecutor` pool.
- `friend/service/FriendService.java` — publish events.
- `group/service/GroupService.java` — publish events.
- `message/service/ChatService.java` — publish event.
- `group/service/GroupChatService.java` — publish event.

---

## 3. Entity layer

### `NotificationType.java`

```java
package com.chatflow.notification.entity;

public enum NotificationType {
    FRIEND_REQUEST,
    FRIEND_REQUEST_ACCEPTED,
    GROUP_MEMBER_ADDED,
    GROUP_MEMBER_REMOVED,
    GROUP_ROLE_CHANGED,
    GROUP_OWNERSHIP_TRANSFERRED,
    NEW_MESSAGE,
    NEW_GROUP_MESSAGE
}
```

### `ReferenceType.java`

Tells the client what `referenceId` points to, so it can deep-link.

```java
package com.chatflow.notification.entity;

public enum ReferenceType {
    FRIENDSHIP,
    GROUP,
    CONVERSATION,
    MESSAGE
}
```

### `Notification.java`

```java
package com.chatflow.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                // feed: newest-first per recipient
                @Index(name = "idx_notif_recipient_created",
                        columnList = "recipient_id, created_at"),
                // unread badge + coalescing lookup
                @Index(name = "idx_notif_recipient_read",
                        columnList = "recipient_id, read")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Who sees this notification. */
    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    /** Who caused it (nullable for system notifications). */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", updatable = false)
    private ReferenceType referenceType;

    /** ID of the friendship / group / conversation / message this points at. */
    @Column(name = "reference_id")
    private UUID referenceId;

    /** Denormalized snippet for the feed (e.g. "Alice: hey there"). */
    @Column(length = 280)
    private String preview;

    /** Coalescing counter: how many underlying events this row represents. */
    @Column(name = "event_count", nullable = false)
    @Builder.Default
    private int eventCount = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRead() {
        if (!read) {
            read = true;
            readAt = Instant.now();
        }
    }

    /** Bump an existing unread row when a new event coalesces into it. */
    public void coalesce(String newPreview) {
        this.eventCount += 1;
        this.preview = newPreview;
        this.createdAt = Instant.now(); // re-float to top of feed
    }
}
```

> **Note on `read` + Hibernate:** the column is named `read`, which is reserved in
> some SQL dialects. On PostgreSQL it is fine unquoted, but if you switch dialects
> rename the field/column to `is_read` to be safe.

---

## 4. Repository

### `NotificationRepository.java`

```java
package com.chatflow.notification.repository;

import com.chatflow.notification.entity.Notification;
import com.chatflow.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // ---- Feed (keyset pagination, newest first) ----

    @Query("""
            select n from Notification n
            where n.recipientId = :recipientId
              and (:cursor is null or n.createdAt < :cursor)
            order by n.createdAt desc
            """)
    List<Notification> findFeed(@Param("recipientId") UUID recipientId,
                                @Param("cursor") Instant cursor,
                                Pageable pageable);

    // ---- Unread badge ----

    long countByRecipientIdAndReadFalse(UUID recipientId);

    // ---- Coalescing lookup: existing unread row for same (recipient, ref, type) ----

    Optional<Notification> findFirstByRecipientIdAndReferenceIdAndTypeAndReadFalse(
            UUID recipientId, UUID referenceId, NotificationType type);

    // ---- Mark read ----

    @Modifying
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :now
             where n.id = :id and n.recipientId = :recipientId and n.read = false
            """)
    int markRead(@Param("id") UUID id,
                 @Param("recipientId") UUID recipientId,
                 @Param("now") Instant now);

    @Modifying
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :now
             where n.recipientId = :recipientId and n.read = false
            """)
    int markAllRead(@Param("recipientId") UUID recipientId,
                    @Param("now") Instant now);

    int deleteByIdAndRecipientId(UUID id, UUID recipientId);
}
```

---

## 5. DTOs

### `NotificationResponse.java`

```java
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
```

### `UnreadCountResponse.java`

```java
package com.chatflow.notification.dto;

public record UnreadCountResponse(long count) {}
```

---

## 6. Event

### `NotificationEvent.java`

Published by business services inside their `@Transactional` method. Carries a list
of recipients so a single group action fans out in one event.

```java
package com.chatflow.notification.event;

import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class NotificationEvent {

    /** Who should receive the notification. */
    private final List<UUID> recipientIds;

    /** Who caused it (nullable). */
    private final UUID actorId;

    private final NotificationType type;
    private final ReferenceType referenceType;
    private final UUID referenceId;
    private final String preview;

    /** Whether this type coalesces into an existing unread row (messages). */
    private final boolean coalesce;
}
```

---

## 7. Listener — persist + push

### `NotificationEventListener.java`

```java
package com.chatflow.notification.listener;

import com.chatflow.notification.event.NotificationEvent;
import com.chatflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * Runs only after the business transaction commits, on a dedicated pool, so
     * notification persistence + WebSocket fan-out never block request threads
     * and never fire for rolled-back data.
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(NotificationEvent event) {
        try {
            notificationService.createAndPush(event);
        } catch (Exception e) {
            // Never let a notification failure surface anywhere; the business
            // action already committed successfully.
            log.error("Failed to process NotificationEvent type={} refId={}",
                    event.getType(), event.getReferenceId(), e);
        }
    }
}
```

> **Why `@TransactionalEventListener(AFTER_COMMIT)` and not the manual `AfterCommit`
> helper?** The listener persists in its *own* transaction (the original is already
> committed), so it needs `@Transactional(REQUIRES_NEW)` in the service — see below.
> The event style also keeps the four business services free of notification deps.

---

## 8. Service — create, coalesce, push, read

### `NotificationService.java`

```java
package com.chatflow.notification.service;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.notification.dto.NotificationResponse;
import com.chatflow.notification.entity.Notification;
import com.chatflow.notification.event.NotificationEvent;
import com.chatflow.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final WebSocketGateway webSocketGateway;

    // ---------- write path (called by the listener) ----------

    /**
     * Runs in a NEW transaction because the originating business tx is already
     * committed by the time the AFTER_COMMIT listener fires.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndPush(NotificationEvent event) {
        for (UUID recipientId : event.getRecipientIds()) {
            if (recipientId.equals(event.getActorId())) {
                continue; // never notify yourself
            }
            Notification saved = upsert(recipientId, event);
            push(recipientId, saved);
        }
    }

    private Notification upsert(UUID recipientId, NotificationEvent event) {
        if (event.isCoalesce() && event.getReferenceId() != null) {
            var existing = repository
                    .findFirstByRecipientIdAndReferenceIdAndTypeAndReadFalse(
                            recipientId, event.getReferenceId(), event.getType());
            if (existing.isPresent()) {
                Notification n = existing.get();
                n.coalesce(event.getPreview());
                return repository.save(n);
            }
        }

        Notification n = Notification.builder()
                .recipientId(recipientId)
                .actorId(event.getActorId())
                .type(event.getType())
                .referenceType(event.getReferenceType())
                .referenceId(event.getReferenceId())
                .preview(event.getPreview())
                .build();
        try {
            return repository.save(n);
        } catch (DataIntegrityViolationException e) {
            // A concurrent event coalesced first; retry the lookup once.
            return repository
                    .findFirstByRecipientIdAndReferenceIdAndTypeAndReadFalse(
                            recipientId, event.getReferenceId(), event.getType())
                    .map(found -> { found.coalesce(event.getPreview());
                                    return repository.save(found); })
                    .orElseThrow(() -> e);
        }
    }

    private void push(UUID recipientId, Notification n) {
        webSocketGateway.sendToUser(recipientId,
                OutboundMessage.of(OutboundMessage.Type.NOTIFICATION,
                        NotificationResponse.from(n)));
    }

    // ---------- read path (called by the controller) ----------

    @Transactional(readOnly = true)
    public List<NotificationResponse> feed(UUID recipientId, Instant cursor, int limit) {
        return repository.findFeed(recipientId, cursor, PageRequest.of(0, limit))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markRead(UUID recipientId, UUID notificationId) {
        int updated = repository.markRead(notificationId, recipientId, Instant.now());
        if (updated > 0) {
            notifyReadState(recipientId);
        }
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        repository.markAllRead(recipientId, Instant.now());
        notifyReadState(recipientId);
    }

    @Transactional
    public void delete(UUID recipientId, UUID notificationId) {
        repository.deleteByIdAndRecipientId(notificationId, recipientId);
    }

    /** Push the new unread count so other devices update their badge live. */
    private void notifyReadState(UUID recipientId) {
        webSocketGateway.sendToUser(recipientId,
                OutboundMessage.of(OutboundMessage.Type.NOTIFICATION_READ,
                        unreadCount(recipientId)));
    }
}
```

---

## 9. Controller

### `NotificationController.java`

```java
package com.chatflow.notification.controller;

import com.chatflow.notification.dto.NotificationResponse;
import com.chatflow.notification.dto.UnreadCountResponse;
import com.chatflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
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

    /** Keyset feed: pass the createdAt of the last item as {@code cursor}. */
    @GetMapping
    public List<NotificationResponse> feed(
            @RequestParam(required = false) Instant cursor,
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
```

### New endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/notifications?cursor=&limit=` | Paginated feed, newest first |
| GET | `/api/notifications/unread-count` | Badge count |
| POST | `/api/notifications/{id}/read` | Mark one read |
| POST | `/api/notifications/read-all` | Mark all read |
| DELETE | `/api/notifications/{id}` | Dismiss |

---

## 10. WebSocket changes

### `OutboundMessage.java` — add to `Type` enum

```java
        // Notifications
        NOTIFICATION,
        NOTIFICATION_READ
```

- `NOTIFICATION` → payload is a `NotificationResponse`.
- `NOTIFICATION_READ` → payload is the new unread `count` (keeps multiple devices in sync).

---

## 11. Async pool

### `AsyncConfig.java` — add bean

```java
    @Bean(name = "notificationExecutor", destroyMethod = "shutdown")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notif-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
```

---

## 12. Wiring the triggers (publish sites)

Each business service gets `ApplicationEventPublisher` injected and publishes inside
its existing `@Transactional` method. The listener handles the rest after commit.

### `FriendService`

```java
// in sendRequest(...) — recipient is targetId, actor is callerId
eventPublisher.publishEvent(NotificationEvent.builder()
        .recipientIds(List.of(targetId))
        .actorId(callerId)
        .type(NotificationType.FRIEND_REQUEST)
        .referenceType(ReferenceType.FRIENDSHIP)
        .referenceId(friendship.getId())
        .preview("sent you a friend request")
        .coalesce(false)
        .build());

// in acceptRequest(...) — recipient is otherUserId (original requester)
eventPublisher.publishEvent(NotificationEvent.builder()
        .recipientIds(List.of(otherUserId))
        .actorId(callerId)
        .type(NotificationType.FRIEND_REQUEST_ACCEPTED)
        .referenceType(ReferenceType.FRIENDSHIP)
        .referenceId(friendship.getId())
        .preview("accepted your friend request")
        .coalesce(false)
        .build());
```

### `GroupService`

Mirror the existing `AfterCommit` fan-out recipient lists (`recipients` /
`uniqueMemberIds`) in `addMember`, `removeMember`, `updateMemberRole`,
`transferOwnership` with the matching `GROUP_*` type, `ReferenceType.GROUP`, and
`referenceId = groupId`. `coalesce(false)`.

### `ChatService.sendMessage`

```java
// receiver = request receiver, actor = senderId, ref = conversation
eventPublisher.publishEvent(NotificationEvent.builder()
        .recipientIds(List.of(receiverId))
        .actorId(senderId)
        .type(NotificationType.NEW_MESSAGE)
        .referenceType(ReferenceType.CONVERSATION)
        .referenceId(conversation.getId())
        .preview(truncate(response.getContent()))
        .coalesce(true)   // collapse a burst into one feed row
        .build());
```

### `GroupChatService`

```java
// recipients = all group members except sender, ref = group
eventPublisher.publishEvent(NotificationEvent.builder()
        .recipientIds(memberIdsExceptSender)
        .actorId(senderId)
        .type(NotificationType.NEW_GROUP_MESSAGE)
        .referenceType(ReferenceType.GROUP)
        .referenceId(groupId)
        .preview(truncate(response.getContent()))
        .coalesce(true)
        .build());
```

> `truncate(...)` is a small helper that caps the preview at the 280-char column
> length. Put it in a `NotificationPreviews` util or inline it.

---

## 13. Behavior notes & edge cases

- **No self-notifications.** `createAndPush` skips `recipientId == actorId`. Message
  triggers already exclude the sender by recipient selection; this is a backstop.
- **Coalescing.** Message notifications collapse into a single unread row per
  `(recipient, conversation/group)`; `eventCount` tracks how many messages it
  represents and `createdAt` re-floats it to the top. Reading it and receiving a new
  message afterward creates a fresh row.
- **Concurrency.** Two messages arriving at once could both miss the coalesce lookup;
  the `DataIntegrityViolationException` retry handles it. (Add a partial unique index
  on `(recipient_id, reference_id, type) where read = false` if you want a hard
  guarantee — optional in v1.)
- **Rollback safety.** Notifications are created only `AFTER_COMMIT`, so a failed
  message send never produces a phantom notification.
- **Multi-device.** `NOTIFICATION_READ` pushes the new unread count so a second
  device's badge updates when the first marks something read.
- **Cross-server.** `WebSocketGateway` already relays through `CrossServerRelay`, so
  live push works when recipient and sender are on different instances.

---

## 14. Build / test checklist

1. Add entity, enums, repository, DTOs, event, listener, service, controller.
2. Extend `OutboundMessage.Type` and `AsyncConfig`.
3. Inject `ApplicationEventPublisher` + add publish calls in the 4 services.
4. Start app → confirm Hibernate creates the `notifications` table.
5. Manual flow:
   - Send a friend request → recipient gets `NOTIFICATION` over WS **and** a feed row.
   - Recipient offline → row exists; appears on `GET /api/notifications`.
   - Send 5 messages in one conversation → one coalesced unread row, `eventCount = 5`.
   - `POST /{id}/read` → `unread-count` drops; `NOTIFICATION_READ` pushed.
6. Unit-test `NotificationService.upsert` coalescing + self-notify skip.

---

## 15. Deferred to v2

- Suppress message notifications for the conversation the recipient is actively viewing
  (use presence / `conversationOpen` signal).
- `GROUP_DELETED` / `FRIEND_REMOVED` notifications.
- Per-user notification preferences (mute a conversation/group).
- Retention/cleanup job for old read notifications.
- Hard DB-level uniqueness for coalescing via partial unique index.
