package com.chatflow.media.processing;
import com.chatflow.group.repository.GroupMemberRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.message.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailEventListener {

    private final MediaMessageRepository  mediaMessageRepository;
    private final ConversationRepository  conversationRepository;
    private final GroupMemberRepository   groupMemberRepository;
    private final WebSocketGateway        webSocketGateway;

    @Async("mediaProcessingExecutor")
    @EventListener
    @Transactional
    public void onThumbnailGenerated(ThumbnailGeneratedEvent event) {
        mediaMessageRepository.findByIdAndDeletedFalse(event.getMediaMessageId())
                .ifPresentOrElse(
                        message -> persist(message, event.getThumbnailUrl()),
                        () -> log.warn("ThumbnailGeneratedEvent for unknown mediaId={}",
                                event.getMediaMessageId())
                );
    }

    private void persist(MediaMessage message, String thumbnailUrl) {
        mediaMessageRepository.updateThumbnailUrl(message.getId(), thumbnailUrl);
        log.debug("Saved thumbnail url mediaId={}", message.getId());
        pushUpdate(message, thumbnailUrl);
    }

    private void pushUpdate(MediaMessage message, String thumbnailUrl) {
        OutboundMessage frame = OutboundMessage.of(
                OutboundMessage.Type.MEDIA_THUMBNAIL_READY,
                Map.of(
                        "mediaMessageId", message.getId(),
                        "thumbnailUrl",   thumbnailUrl,
                        "messageType",    message.getMessageType()
                )
        );

        if (message.getConversationId() != null) {
            // Resolve both participants from the conversation
            conversationRepository.findById(message.getConversationId())
                    .ifPresent(conv -> {
                        webSocketGateway.sendToUser(conv.getParticipantOneId(), frame);
                        webSocketGateway.sendToUser(conv.getParticipantTwoId(), frame);
                    });
        } else if (message.getGroupId() != null) {
            List<UUID> memberIds = groupMemberRepository
                    .findUserIdsByGroupId(message.getGroupId());
            webSocketGateway.sendToUsers(memberIds, frame);
        }
    }
}