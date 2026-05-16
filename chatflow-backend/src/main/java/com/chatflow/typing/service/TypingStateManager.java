package com.chatflow.typing.service;

import com.chatflow.typing.dto.TypingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final ScheduledExecutorService scheduler;
    private final SimpMessagingTemplate messagingTemplate;

    public TypingStateManager(
            @Qualifier("typingTimerExecutor") ScheduledExecutorService scheduler,
            SimpMessagingTemplate messagingTemplate) {
        this.scheduler = scheduler;
        this.messagingTemplate = messagingTemplate;
    }

    public void handleTyping(UUID conversationId, UUID userId, boolean typing) {
        if (typing) {
            boolean changed = setState(conversationId, userId, true);
            if (changed) {
                broadcast(conversationId, userId, true);
            }
            scheduleExpiry(conversationId, userId,
                    () -> onTimerExpired(conversationId, userId));
        } else {
            boolean changed = setState(conversationId, userId, false);
            cancelTimer(conversationId, userId);
            if (changed) {
                broadcast(conversationId, userId, false);
            }
        }
    }

    public void clearAllForUser(UUID userId) {
        typingState.keySet().stream()
                .filter(key -> key.userId().equals(userId))
                .forEach(key -> {
                    boolean wasTyping = Boolean.TRUE.equals(typingState.remove(key));
                    cancelTimer(key.conversationId(), userId);
                    if (wasTyping) {
                        broadcast(key.conversationId(), userId, false);
                    }
                });
        log.debug("Cleared all typing state for userId={}", userId);
    }

    public boolean isTyping(UUID conversationId, UUID userId) {
        return Boolean.TRUE.equals(typingState.get(new TypingKey(conversationId, userId)));
    }

    private void onTimerExpired(UUID conversationId, UUID userId) {
        TypingKey key = new TypingKey(conversationId, userId);
        boolean wasTyping = Boolean.TRUE.equals(typingState.remove(key));
        timers.remove(key);
        if (wasTyping) {
            log.debug("Typing timer expired userId={} conversationId={}", userId, conversationId);
            broadcast(conversationId, userId, false);
        }
    }

    private void broadcast(UUID conversationId, UUID userId, boolean typing) {
        String topic = "/topic/typing." + conversationId;
        messagingTemplate.convertAndSend(topic, TypingEvent.of(conversationId, userId, typing));
        log.debug("Broadcast typing={} userId={} to topic={}", typing, userId, topic);
    }

    private boolean setState(UUID conversationId, UUID userId, boolean typing) {
        TypingKey key = new TypingKey(conversationId, userId);
        Boolean previous = typing
                ? typingState.put(key, true)
                : typingState.remove(key);
        boolean changed = typing ? (previous == null || !previous) : (previous != null);
        log.debug("Typing state userId={} conversationId={} typing={} changed={}",
                userId, conversationId, typing, changed);
        return changed;
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

    record TypingKey(UUID conversationId, UUID userId) {}
}