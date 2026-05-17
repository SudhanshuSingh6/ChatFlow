package com.chatflow.typing.service;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.message.entity.Conversation;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.typing.dto.TypingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TypingStateManager {

    static final long TYPING_TIMEOUT_SECONDS = 4;

    private final Map<TypingKey, Boolean> typingState = new ConcurrentHashMap<>();
    private final Map<TypingKey, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final Map<TypingKey, UUID> receiverMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private final WebSocketGateway webSocketGateway;
    private final ConversationRepository conversationRepository;

    public TypingStateManager(
            @Qualifier("typingTimerExecutor") ScheduledExecutorService scheduler,
            WebSocketGateway webSocketGateway,
            ConversationRepository conversationRepository) {
        this.scheduler = scheduler;
        this.webSocketGateway = webSocketGateway;
        this.conversationRepository = conversationRepository;
    }

    public void handleTyping(UUID conversationId, UUID userId, boolean typing) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        if (!isParticipant(conversation, userId)) {
            throw new SecurityException("User " + userId
                    + " is not a participant in conversation " + conversationId);
        }

        UUID receiverId = resolveReceiver(conversation, userId);
        TypingKey key = new TypingKey(conversationId, userId);
        receiverMap.put(key, receiverId);

        if (typing) {
            boolean changed = setState(conversationId, userId, true);
            if (changed) {
                broadcast(conversationId, userId, receiverId, true);
            }
            scheduleExpiry(conversationId, userId,
                    () -> onTimerExpired(conversationId, userId));
        } else {
            boolean changed = setState(conversationId, userId, false);
            cancelTimer(conversationId, userId);
            receiverMap.remove(key);
            if (changed) {
                broadcast(conversationId, userId, receiverId, false);
            }
        }
    }

    public void clearAllForUser(UUID userId) {
        typingState.keySet().stream()
                .filter(key -> key.userId().equals(userId))
                .toList()
                .forEach(key -> {
                    boolean wasTyping = Boolean.TRUE.equals(typingState.remove(key));
                    cancelTimer(key.conversationId(), userId);
                    UUID receiverId = receiverMap.remove(key);
                    if (wasTyping && receiverId != null) {
                        broadcast(key.conversationId(), userId, receiverId, false);
                    }
                });

        log.debug("Cleared typing state for userId={}", userId);
    }

    public boolean isTyping(UUID conversationId, UUID userId) {
        return Boolean.TRUE.equals(typingState.get(new TypingKey(conversationId, userId)));
    }

    private void onTimerExpired(UUID conversationId, UUID userId) {
        TypingKey key = new TypingKey(conversationId, userId);
        boolean wasTyping = Boolean.TRUE.equals(typingState.remove(key));
        timers.remove(key);

        UUID receiverId = receiverMap.remove(key);
        if (wasTyping && receiverId != null) {
            broadcast(conversationId, userId, receiverId, false);
        }
    }

    private void broadcast(UUID conversationId, UUID userId, UUID receiverId, boolean typing) {
        webSocketGateway.sendToUser(receiverId,
                OutboundMessage.of(OutboundMessage.Type.TYPING,
                        TypingEvent.of(conversationId, userId, typing)));
    }

    private boolean setState(UUID conversationId, UUID userId, boolean typing) {
        TypingKey key = new TypingKey(conversationId, userId);
        Boolean previous = typing
                ? typingState.put(key, true)
                : typingState.remove(key);

        return typing ? previous == null || !previous : previous != null;
    }

    private void scheduleExpiry(UUID conversationId, UUID userId, Runnable onExpire) {
        TypingKey key = new TypingKey(conversationId, userId);
        ScheduledFuture<?> existing = timers.get(key);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = scheduler.schedule(
                onExpire, TYPING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timers.put(key, future);
    }

    private void cancelTimer(UUID conversationId, UUID userId) {
        ScheduledFuture<?> timer = timers.remove(new TypingKey(conversationId, userId));
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private boolean isParticipant(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                || userId.equals(conversation.getParticipantTwoId());
    }

    private UUID resolveReceiver(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                ? conversation.getParticipantTwoId()
                : conversation.getParticipantOneId();
    }

    record TypingKey(UUID conversationId, UUID userId) {}
}